import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class ro2021recv {
    public static void main(String[] args) {

        // Se comprueba la cantidad correcta de parámetros
        if (args.length != 2) {
            System.out.println("\nSintaxis correcta: java ro2021recv output_file listen_port");
            return;
        }

        // Se obtiene el puerto a partir del argumento indicado por línea de comandos
        int serverPort = Integer.parseInt(args[1]);

        // Se abre el fichero
        FileOutputStream file;
        try {
            file = new FileOutputStream(args[0]);
        } catch (FileNotFoundException e) {
            System.out.println("Error en la creación del fichero");
            return;
        }

        // Se crea el socket
        DatagramSocket datagramSocket;
        try {
            datagramSocket = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            System.out.println("Error en la creación del socket");
            return;
        }

        // Número de secuencia esperado
        short expectedSequenceNumber = 1;
        // End of File
        boolean EOF = false;
        // Array de bytes para la información recibida
        byte[] receivedBytes = new byte[1472];
        // Paquete entrante (datos)
        DatagramPacket inDP = new DatagramPacket(receivedBytes, receivedBytes.length);
        // ByteBuffer para los datos recibidos
        ByteBuffer receivedBuffer;
        // Array de bytes con la IP + puerto del emisor
        byte[] source = new byte[6];
        // Array de bytes con los datos recibidos
        byte[] data;
        // ByteBuffer para el ACK a enviar
        ByteBuffer ACK = ByteBuffer.allocate(12);
        // Array de bytes con el ACK
        byte[] ACKBytes;
        // Paquete de salida (ACK)
        DatagramPacket outDP;

        // Bucle principal
        while (true) {

            // Se espera a que llegue un paquete
            try {
                datagramSocket.receive(inDP);
            } catch (IOException e) {
                System.out.println("Error en la recepción del paquete");
                datagramSocket.close();
                return;
            }

            // Se comprueba si es el paquete que marca el final de la transmisión
            if (inDP.getLength() < 1472) {
                // Se marca el fin de la transmisión
                EOF = true;
            }

            // Se crea un ByteBuffer del paquete
            receivedBuffer = ByteBuffer.wrap(receivedBytes);

            // Se comprueba si es el paquete esperado
            if (receivedBuffer.getShort(6) != expectedSequenceNumber) {
                // Se reenvía el ACK con el RTT actualizado
                ACK.putInt(8, receivedBuffer.getInt(8));
                ACKBytes = ACK.array();
                outDP = new DatagramPacket(ACKBytes, ACKBytes.length, inDP.getAddress(), inDP.getPort());
                try {
                    datagramSocket.send(outDP);
                } catch (IOException e) {
                    System.out.println("Error al enviar el ACK");
                    datagramSocket.close();
                    return;
                }
                continue;
            }

            // Se extrae la información del destino (IP + puerto) y se guarda en un array de
            // bytes
            receivedBuffer.get(source, 0, 6);

            // Se extrae el resto del paquete (los datos reales) y se guardan en un array de
            // bytes
            receivedBuffer.position(12);
            data = new byte[inDP.getLength() - 12];
            receivedBuffer.get(data, 0, data.length);

            // Se escriben los datos en el fichero
            try {
                file.write(data);
            } catch (IOException e) {
                System.out.println("Error en la escritura en el fichero");
                datagramSocket.close();
                return;
            }

            // Se crea la cabecera de aplicación + número de secuencia (dest[6] +
            // sequenceNumber[2] + RTT[4] = 12 bytes)
            ACK.rewind();
            ACK.put(source);
            ACK.putShort(expectedSequenceNumber);
            ACK.putInt(receivedBuffer.getInt(8));

            // Se pasa a un array de bytes
            ACKBytes = ACK.array();

            // Se crea el paquete de salida y se envía al router
            outDP = new DatagramPacket(ACKBytes, ACKBytes.length, inDP.getAddress(), inDP.getPort());
            try {
                datagramSocket.send(outDP);
            } catch (IOException e) {
                System.out.println("Error al enviar el ACK");
                datagramSocket.close();
                return;
            }

            // Se comprueba si es el fin de la transmisión
            if (EOF) {
                // Se cierra el fichero
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("Error al cerrar el fichero");
                }

                // Se cierra el socket
                datagramSocket.close();
                return;
            }

            // Se actualiza el número de secuencia esperado en la siguiente transmisión
            expectedSequenceNumber++;
        }
    }
}
