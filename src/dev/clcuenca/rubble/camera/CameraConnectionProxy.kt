package dev.clcuenca.rubble.camera

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.IllegalBlockingModeException
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * Class to create a establish a connection with a dev.clcuenca.rubble.camera in order to send commands
 * through the Milestone Server.
 *
 * @author Carlos L. Cuenca
 * @date 08/17/2020
 */
class CameraConnectionProxy {

    /// --------------
    /// Static Members

    companion object {

        const val DEFAULT_TIMEOUT    = 4000
        const val DEFAULT_REQUEST_ID = 0
        const val DEFAULT_PORT       = 7563

        const val INVALID_GUID_MESSAGE  = "The provided GUID is invalid"
        const val INVALID_TOKEN_MESSAGE = "The provided Token is invalid"

        /**
         * Returns a byte array of the customized Connection request
         */
        fun connectionRequest(requestId: Int, guid: String, token: String): ByteArray {

            return ("<?xml version=\"1.0\" encoding=\"utf-8\"?><methodcall><requestid>$requestId</requestid>" +
                    "<methodname>connect</methodname><username>dummy</username><password>dummy</password>" +
                    "<cameraid>$guid</cameraid><connectparam>id=$guid&amp;connectiontoken=$token</connectparam>" +
                    "</methodcall>\r\n\r\n").toByteArray()

        }

        /**
         * Returns a boolean value indicating if the given GUID is valid
         */
        fun isValidGUID(guid: String): Boolean {

            return guid.isNotBlank() && guid.isNotEmpty()

        }

        /**
         * Returns a boolean value indicating if the token is valid
         */
        fun isValidToken(token: String): Boolean {

            return token.isNotEmpty() && token.isNotBlank()

        }

    }

    /// --------------
    /// Public Members

    /**
     * The String guid of the Camera we want to connect to
     */
    var guid     : String = ""

    /**
     * The user's dev.clcuenca.rubble.authentication token that was retrieved from a successful login
     */
    var token    : String = ""

    /**
     * The server's hostname
     */
    var hostname : String = ""

    /**
     * The port the socket will establish on
     */
    var port     : Int    = DEFAULT_PORT

    var listener : Listener? = null

    /**
     * Attempts to create a new socket to the server with the given host name & port.
     * This method requires that the connection be given a valid token after authenticating
     * the user, and will always attempt to create a new connection, thus, it is the implementor's
     * responsibility to close the socket elsewhere
     */
    fun connect() {

        var socket : Socket?

        thread (start = true) {

            socket = Socket()

            try {

                socket?.connect(InetSocketAddress(hostname, port),
                    DEFAULT_TIMEOUT
                )

                val outputStream = socket?.getOutputStream()

                if(!isValidGUID(guid)) {

                    throw InvalidGUIDException(INVALID_GUID_MESSAGE)

                }

                if(!isValidToken(token)) {

                    throw InvalidTokenException(INVALID_TOKEN_MESSAGE)

                }

                outputStream?.write(
                    connectionRequest(
                        DEFAULT_REQUEST_ID,
                        guid,
                        token
                    )
                )

                outputStream?.flush()

                // We get the response from the server to validate the connection request
                val inputStream = socket?.getInputStream()
                val reader      = BufferedReader(InputStreamReader(inputStream))
                val response    = reader.readLine()

                listener?.cameraConnectionEstablished(guid, socket)

            } catch(ioException: IOException) {

                socket?.close()

                socket = null

                listener?.cameraConnectionFailed(guid)

            } catch (timeoutException: TimeoutException) {

                socket?.close()

                socket = null

                listener?.cameraConnectionFailed(guid)

            } catch (illegalBlockingModeException: IllegalBlockingModeException) {

                socket?.close()

                socket = null

                listener?.cameraConnectionFailed(guid)

            } catch (illegalArgumentException: IllegalArgumentException) {

                socket?.close()

                socket = null

                listener?.cameraConnectionFailed(guid)

            }

        }

    }

    /// ----------
    /// Interfaces

    interface Listener {

        fun cameraConnectionEstablished(cameraGUID: String, cameraSocket: Socket?)
        fun cameraConnectionFailed(cameraGUID: String)

    }

    /// ----------
    /// Exceptions

    class ConnectionErrorException (message: String): Exception(message)
    class InvalidGUIDException     (message: String): Exception(message)
    class InvalidTokenException    (message: String): Exception(message)

}