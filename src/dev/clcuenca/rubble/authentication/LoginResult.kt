package dev.clcuenca.rubble.authentication

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement
class LoginResult {

    @JacksonXmlProperty(isAttribute = true)
    var token            : String = ""

    @JacksonXmlProperty(isAttribute = true)
    var registrationTime : String = ""

    @JacksonXmlProperty(isAttribute = true)
    var timeToLive       : Long = 0L

}