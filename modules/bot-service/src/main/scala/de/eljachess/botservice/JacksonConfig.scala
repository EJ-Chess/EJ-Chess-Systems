package de.eljachess.botservice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

@Singleton
class JacksonConfig extends ObjectMapperCustomizer:
  def customize(mapper: ObjectMapper): Unit =
    mapper.registerModule(DefaultScalaModule)
