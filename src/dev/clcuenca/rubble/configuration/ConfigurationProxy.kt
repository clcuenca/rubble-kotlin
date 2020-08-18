package dev.clcuenca.rubble.configuration

import dev.clcuenca.rubble.authentication.BasicAuthenticationProxy
import dev.clcuenca.rubble.authentication.LoginResult
import dev.clcuenca.rubble.camera.Camera
import org.w3c.dom.Document
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread

/**
 * Pulls the entire configuration of a server and callback with a [ServerConfiguration]
 * instance with the applicable data.
 *
 * @author Carlos L. Cuenca
 * @date 08/17/2020
 */
class ConfigurationProxy {

    /// --------------
    /// Static Members

    companion object {

        /// ----------------------
        /// Private Static Members

        private const val API_ADDRESS           = "/ManagementServer/ServerCommandService.svc"
        private const val SOAP_ACTION           = "http://videoos.net/2/XProtectCSServerCommand/IServerCommandService/GetConfiguration"
        private const val HEADER_SOAP_ACTION    = "SOAPAction"
        private const val HEADER_CONTENT_LENGTH = "Content-Length"
        private const val HEADER_CONTENT_TYPE   = "Content-Type"
        private const val HEADER_AUTHORIZATION  = "Authorization"
        private const val KEYSTORE_BKS          = "BKS"
        private const val PROTOCOL_SSL          = "SSL"
        private const val CONTENT_TYPE          = "text/xml; charset=utf-8"
        private const val EMPTY_TOKEN           = ""

        /// -------
        /// Indices

        const val COVERAGE_DEPTH         = 0
        const val COVERAGE_DIRECTION     = 1
        const val COVERAGE_FIELD_OF_VIEW = 2
        const val DESCRIPTION            = 3
        const val DEVICE_ID              = 4
        const val DEVICE_INDEX           = 5
        const val GIS_POINT              = 6
        const val HARDWARE_ID            = 7
        const val NAME                   = 9
        const val RECORDER_ID            = 10
        const val SHORT_NAME             = 11

        /// ----------------------
        /// Private Static Methods

        /**
         * Returns a random uuid string
         * @return String uuid
         */
        private fun randomUUIDString(): String {

            return UUID.randomUUID().toString()

        }

        /**
         * Returns the SOAP message with the given token to
         * retrieve the server's configuration
         *
         * @param token A previously used token, or blank if the first time
         */
        private fun getSoapConfiguration(token: String): String {

            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<soap:Body>" +
                    "<GetConfiguration xmlns=\"http://videoos.net/2/XProtectCSServerCommand\">" +
                    "<currentToken>${token}</currentToken>" +
                    "</GetConfiguration>" +
                    "</soap:Body>" +
                    "</soap:Envelope>"

        }

    }

    /**
     * The server's host name
     */
    var hostname : String    = ""

    /**
     * Basic user's username
     */
    var username : String    = ""

    /**
     * Basic User's password
     */
    var password : String    = ""

    /**
     * Authentication token to be used when retrieving the Server configuration.
     * The token is retrieved from the server with a [BasicAuthenticationProxy]
     */
    var token    : String    = ""

    /**
     * The listener that will receive callbacks based on the results returned from the server
     */
    var listener : Listener? = null

    /// ---------------
    /// Private Members

    /**
     * Returns the Base64 encoded credentials of username and password
     */
    private val base64Credentials       : String
        get() {

            return Base64.getEncoder().encodeToString("[BASIC]\\$username:$password".toByteArray(
                    StandardCharsets.UTF_8))

        }

    /**
     * Requests the Server Configuration via SSL with a given keystore in an input stream.
     * The given keystore should be the server's CA/Self-Signed certificate if applicable.
     * There are some certificates that are not trusted by Android, so this allows custom
     * certificates generated by a tool (such as BouncyCastle) to be pre-loaded in the app
     * that's used to connect to the server
     *
     * @param keyStoreStream The stream containing the custom keystore
     * @param keystorePassword The password to the keystore
     */
    fun requestSecureConfiguration(keyStoreStream: InputStream, keystorePassword: String) {

        thread(start = true) {

            val url        = URL("https://$hostname:443$API_ADDRESS")
            val connection = url.openConnection() as HttpsURLConnection

            connection.sslSocketFactory = getSecureSSLContext(keyStoreStream, keystorePassword).socketFactory
            connection.hostnameVerifier = HostnameVerifier{ p0, p1 -> true }

            val soapConfiguration = getSoapConfiguration(token)

            connection.setRequestProperty(HEADER_AUTHORIZATION, "Basic $base64Credentials")
            connection.setRequestProperty(HEADER_CONTENT_LENGTH, soapConfiguration.length.toString())
            connection.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE)
            connection.setRequestProperty(HEADER_SOAP_ACTION, SOAP_ACTION)
            connection.requestMethod = "POST"
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(soapConfiguration)
            writer.flush()

            val responseCode    = connection.responseCode
            val responseMessage = connection.responseMessage

            if(responseCode != HttpsURLConnection.HTTP_OK) {

                listener?.configurationRetrievalFailed(responseMessage)

            } else {

                val returnSoap = readInputStream(connection.inputStream)

                listener?.configurationRetrievalSuccessful(parseSuccessXML(returnSoap))

            }

        }

    }

    /***
     * Requests the Server Configuration via an unsecure SSL Tunnel by trusting all certificates.
     * This method is not recommended to be used
     */
    fun requestUnsecureConfiguration() {

        thread(start = true) {

            val url        = URL("https://$hostname:443$API_ADDRESS")
            val connection = url.openConnection() as HttpsURLConnection

            connection.sslSocketFactory = getUnsecureSSLContext().socketFactory
            connection.hostnameVerifier = HostnameVerifier{ p0, p1 -> true }

            val soapConfiguration = getSoapConfiguration(token)

            connection.setRequestProperty(HEADER_AUTHORIZATION, "Basic $base64Credentials")
            connection.setRequestProperty(HEADER_CONTENT_LENGTH, soapConfiguration.length.toString())
            connection.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE)
            connection.setRequestProperty(HEADER_SOAP_ACTION, SOAP_ACTION)
            connection.requestMethod = "POST"
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(soapConfiguration)
            writer.flush()

            val responseCode    = connection.responseCode
            val responseMessage = connection.responseMessage

            if(responseCode != HttpsURLConnection.HTTP_OK) {

                listener?.configurationRetrievalFailed(responseMessage)

            } else {

                val returnSoap = readInputStream(connection.inputStream)

                listener?.configurationRetrievalSuccessful(parseSuccessXML(returnSoap))

            }

        }

    }

    /// ---------------
    /// Private Methods

    /**
     * Returns a String instance with the contents of the inputStream
     * @param inputStream The contents to be extracted
     */
    private fun readInputStream(inputStream: InputStream) : String {

        val reader        = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = java.lang.StringBuilder()

        for(line in reader.lines()) {

            stringBuilder.append(line)

        }

        reader.close()

        return stringBuilder.toString()

    }

    /**
     * Returns a secure SSL Context with the custom keystore
     *
     * @param inputStream The inputstream containing the custom keystore
     * @param keystorePassword The password to the keystore
     */
    private fun getSecureSSLContext(inputStream: InputStream, keystorePassword: String): SSLContext {

        val keyStore : KeyStore = KeyStore.getInstance(KEYSTORE_BKS)

        try {

            keyStore.load(inputStream, keystorePassword.toCharArray())

        } finally {

            inputStream.close()

        }

        // Create the trust manager that trusts the CA's in our keystore
        val trustManagerFactoryAlgorithm : String = TrustManagerFactory.getDefaultAlgorithm()
        val trustManagerFactory          : TrustManagerFactory = TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm)
        trustManagerFactory.init(keyStore)

        // Creating an SSLSocketFactory That uses our trust manager
        val sslContext : SSLContext = SSLContext.getInstance(PROTOCOL_SSL)
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        return sslContext

    }

    /**
     * Returns an unsecure SSL Context that trusts all certificates
     */
    private fun getUnsecureSSLContext() : SSLContext {

        val trustAllCerts = arrayOf<TrustManager> (object : X509TrustManager {

            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {

            }

            override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {

            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf<X509Certificate>()
            }


        })

        val sslContext : SSLContext = SSLContext.getInstance(PROTOCOL_SSL)
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return sslContext

    }

    /**
     * Parses a successful login's XML and loads the data onto an instance of [LoginResult]
     * to be sent to the applicable [Listener]
     *
     * @param xml The successful response's xml
     */
    private fun parseSuccessXML(xml: String): ServerConfiguration {

        val serverConfiguration = ServerConfiguration()

        val factory  : DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        val builder  : DocumentBuilder = factory.newDocumentBuilder()
        val document : Document = builder.parse(ByteArrayInputStream(xml.toByteArray()))

        val cameras = document.getElementsByTagName("CameraInfo")

        for(index in 0 until cameras.length) {

            // Pull the camera information from the current camera
            val cameraInfo = cameras.item(index).childNodes
            val camera     = Camera()

            for(jindex in 0 until cameraInfo.length) {

                if(jindex == DEVICE_ID) {

                    camera.GUID = cameraInfo.item(jindex).textContent

                }

                if(jindex == NAME) {

                    camera.name = cameraInfo.item(jindex).textContent

                }

            }

            serverConfiguration.cameras.add(camera)

        }

        return serverConfiguration

    }

    /// ----------
    /// Interfaces

    /**
     * Listener interface that callback when the Server has returned a Server Configuration
     */
    interface Listener {

        fun configurationRetrievalSuccessful(serverConfiguration: ServerConfiguration)
        fun configurationRetrievalFailed(message: String)

    }

}