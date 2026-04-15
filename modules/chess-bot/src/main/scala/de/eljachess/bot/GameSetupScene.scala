// $COVERAGE-OFF$ JavaFX cannot be tested headless
package de.eljachess.bot

import de.eljachess.chess.model.Color
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.{GridPane, HBox, VBox}
import javafx.scene.text.{Font, FontWeight, Text}

class GameSetupScene(onStart: GameSetup => Unit) extends Scene(new VBox()):
  private val root = getRoot.asInstanceOf[VBox]

  private val nameField = new TextField()
  nameField.setPromptText("Enter your name")
  nameField.setPrefWidth(200)

  private val colorToggle = new ToggleGroup()
  private val whiteRadio  = new RadioButton("White")
  private val blackRadio  = new RadioButton("Black")
  whiteRadio.setToggleGroup(colorToggle)
  blackRadio.setToggleGroup(colorToggle)
  whiteRadio.setSelected(true)

  private val humanRadio = new RadioButton("Human")
  private val botRadio   = new RadioButton("Bot")
  private val oppToggle  = new ToggleGroup()
  humanRadio.setToggleGroup(oppToggle)
  botRadio.setToggleGroup(oppToggle)
  humanRadio.setSelected(true)

  private val eloBox    = new HBox(8)
  private val eloCombo  = new ComboBox[EloLevel]()
  eloCombo.getItems.addAll(EloLevel.predefined*)
  eloCombo.setValue(EloLevel.Intermediate)
  private val customEloField = new TextField()
  customEloField.setPromptText("Custom ELO (e.g. 1200)")
  customEloField.setPrefWidth(140)
  customEloField.setVisible(false)
  eloBox.getChildren.addAll(eloCombo, customEloField)
  eloBox.setVisible(false)

  private val customItem = new EloLevel(0, "Custom…")
  eloCombo.getItems.add(customItem)
  eloCombo.setOnAction(_ =>
    customEloField.setVisible(eloCombo.getValue == customItem)
  )

  botRadio.setOnAction(_  => eloBox.setVisible(true))
  humanRadio.setOnAction(_ => eloBox.setVisible(false))

  private val startBtn = new Button("Start Game")
  startBtn.setFont(Font.font("System", FontWeight.BOLD, 14))
  startBtn.setOnAction(_ => {
    val name  = if nameField.getText.trim.isEmpty then "Player" else nameField.getText.trim
    val color = if whiteRadio.isSelected then Color.White else Color.Black
    val opp: Opponent =
      if humanRadio.isSelected then HumanOpponent
      else
        val elo =
          if eloCombo.getValue == customItem then
            val raw = customEloField.getText.trim
            if raw.matches("\\d+") then EloLevel.custom(raw.toInt)
            else EloLevel.Intermediate
          else eloCombo.getValue
        BotOpponent(elo)
    onStart(GameSetup(name, color, opp))
  })

  private def grid: GridPane =
    val g = new GridPane()
    g.setHgap(10); g.setVgap(12); g.setPadding(new Insets(10))
    def row(label: String, node: javafx.scene.Node, r: Int): Unit =
      g.add(new Label(label), 0, r); g.add(node, 1, r)
    row("Your name:",  nameField, 0)
    row("Play as:",    new HBox(12, whiteRadio, blackRadio), 1)
    row("Opponent:",   new HBox(12, humanRadio, botRadio), 2)
    row("Difficulty:", eloBox, 3)
    g

  private val title = new Text("New Chess Game")
  title.setFont(Font.font("System", FontWeight.BOLD, 20))

  root.setSpacing(16)
  root.setPadding(new Insets(24))
  root.setAlignment(Pos.CENTER)
  root.getChildren.addAll(title, grid, startBtn)
// $COVERAGE-ON$
