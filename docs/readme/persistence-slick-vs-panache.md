# Persistenz-Vergleich: Slick vs. Panache

Zwei Feature-Branches implementieren dasselbe Persistenz-Interface auf unterschiedliche Weise.

| | **Slick (FRM)** | **Panache / Hibernate (JPA/ORM)** |
|---|---|---|
| **Branch** | `feature/persistence-approach-a` | `feature/persistence-panache-approach` |
| **Ansatz** | Functional Relational Mapping | Object-Relational Mapping |
| **Zentrale Abstraktion** | `TableQuery` + DBIO Actions | `@Entity` + EntityManager |
| **Transaktionen** | Manuell via `Await.result(db.run(...))` | Deklarativ via `@Transactional` |
| **Schema-Migration** | `games.schema.createIfNotExists` | `hibernate.hbm2ddl.auto=update` |
| **Typsicherheit** | Kompilierzeit (Scala-Typen) | Laufzeit (JPA-Annotationen) |
| **Null-Handling** | `Option[T]` nativ in Slick | `java.lang.Integer` + `Option(e.botElo)` |
| **Test-Setup** | `Database.forURL(...)` direkt | `persistence.xml` + `EntityManagerFactory` |
| **Quarkus-Integration** | JDBC DataSource (Agroal) über `Database.forDataSource` | Nativ über `quarkus-hibernate-orm-panache` |

---

## Slick — Kernkonzept

```scala
// Tables.scala — Typsicheres Schema mit profile.api.*
class GamesTable(tag: Tag) extends Table[GameRow](tag, "games"):
  def id  = column[String]("id", O.PrimaryKey)
  def pgn = column[String]("pgn")
  def *   = (id, pgn, playerColor, botColor, botElo).mapTo[GameRow]

// GameRepository.scala — DBIO-Actions, kein ORM-Zauber
def findById(id: String): Option[GameRow] =
  Await.result(db.run(tables.findByIdAction(id)), 5.seconds)
```

**Vorteil:** Slick übersetzt Scala-Ausdrücke direkt in SQL — was kompiliert, ist typkorrekt.  
**Nachteil:** Fremde API, path-dependent types in Scala 3 erfordern `Tables`-Hilfsklasse.

---

## Panache / Hibernate — Kernkonzept

```scala
// GameEntity.scala — JPA-Annotationen beschreiben das Mapping
@Entity @Table(name = "games")
class GameEntity:
  @Id @Column(name = "id") var id: String = _
  @Column(name = "pgn", columnDefinition = "TEXT") var pgn: String = _

// GameRepository.scala — EntityManager, @Transactional
@Transactional
def insert(row: GameRow): Unit =
  em.persist(GameEntity.fromRow(row))
```

**Vorteil:** Bekanntes JPA-Modell, `@Transactional` übernimmt Commit/Rollback automatisch.  
**Nachteil:** Mutable `var`-Felder in Entities, Java-Nullable-Typen (`java.lang.Integer`).

---

## Gemeinsames Interface (GameRepository)

Beide Branches haben **identische Signaturen** in `GameRepository`:

```scala
def insert(row: GameRow): Unit
def findById(id: String): Option[GameRow]
def updatePgn(id: String, pgn: String): Unit
def delete(id: String): Unit
def findAll(): Seq[GameRow]
```

`GameService` ist deshalb auf **beiden Branches identisch** — nur die Implementierung darunter unterscheidet sich.

---

## Entscheidung

Für dieses Projekt wurde **Slick** gewählt (`feature/persistence-approach-a`), weil:

1. Scala-native: `Option[T]`, case classes, keine mutable Entities nötig
2. Typsicherheit zur Kompilierzeit statt zur Laufzeit
3. Kein Hibernate-Bytecode-Enhancement (wichtig bei Scala/GraalVM)
4. Direktes SQL — kein N+1-Problem durch Lazy Loading
