package de.eljachess.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

/** Registers the Jackson Scala module so case classes are serialized correctly. */
@Singleton
class JacksonConfig extends ObjectMapperCustomizer {
  override def customize(mapper: ObjectMapper): Unit = {
    mapper.registerModule(DefaultScalaModule)
  }
}
