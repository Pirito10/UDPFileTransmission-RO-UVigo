import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class ro2021send {
    public static void main(String[] args) {

        // Se obtiene el instante de inicio del programa
        long startTime = System.nanoTime();

        // Se comprueba la cantidad correcta de parámetros
        if (args.length != 5) {
            System.out.println(
                    "\nSintaxis correcta: java ro2021send input_file dest_IP dest_port emulator_IP emulator_port");
            return;
        }

        // Se obtiene la dirección y puerto del router a partir del argumento indicado
        // por línea de comandos
        InetSocketAddress router;
        try {
            router = new InetSocketAddress(InetAddress.getByName(args[3]), Integer.parseInt(args[4]));
        } catch (UnknownHostException e) {
            System.out.println("Error en la dirección destino");
            return;
        }

        // Se pasa la IP destino a un array de bytes
        byte[] bytesIP;
        try {
            bytesIP = InetAddress.getByName(args[1]).getAddress();
        } catch (UnknownHostException e) {
            System.out.println("Error en la dirección destino");
            return;
        }

        // Se pasa el puerto del router a un array de bytes
        byte[] bytesPort = { (byte) (Integer.parseInt(args[2]) >> 8), (byte) Integer.parseInt(args[2]) };

        // Se lee el fichero y se almacena en un array de bytes
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(new File(args[0]).toPath());
        } catch (IOException e) {
            System.out.println("Error en la lectura del fichero");
            return;
        }

        // Canales preparados (1 -> preparado para leer || 0 -> preparado para escribir)
        int readyChannels = 0;
        // Offsets para dividir los paquetes
        int from = 0;
        int to = 1460;
        // Número de secuencia
        short sequenceNumber = 1;
        // ACK pendiente
        boolean pendingACK = false;
        // Retransmisión
        boolean rtx;
        // End of File
        boolean EOF = false;
        // Round Trip Time
        int RTT;
        // Smoothed RTT
        double SRTT;
        // SRTT de la anterior transmisión
        double previousSRTT = 0;
        // Desviación típica del RTT
        double devRTT;
        // devRTT de la anterior transmisión
        double previousDevRTT = 0;
        // Recovery Time Objective (TimeOut)
        int RTO = 100;
        // RTO de la anterior transmisión
        int previousRTO = 0;
        // Tiempo actual
        long currentTime;
        // Tiempo de inicio del timeout
        long startTimeout = 0;
        // Array de bytes con los datos a enviar
        byte[] dataBytes;
        // ByteBuffer para los bytes a enviar
        ByteBuffer toSendBuffer;
        // ByteBuffer para almacenar el ack
        ByteBuffer ACK = ByteBuffer.allocate(12);

        // Se crea el canal
        DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            System.out.println("Error en la creación del canal");
            return;
        }

        // Se crea el selector
        Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.out.println("Error en la creación del selector");
            return;
        }

        // Se establece el canal como no bloqueante
        try {
            channel.configureBlocking(false);
        } catch (IOException e) {
            System.out.println("Error en la configuración del canal");
        }

        // Se registra en el selector el interés de lectura
        try {
            channel.register(selector, SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            System.out.println("Error en la asignación del canal");
            return;
        }

        // Bucle principal
        do {

            // Se comprueba si hay un ACK pendiente
            if (pendingACK) {

                // Se obtiene el tiempo de inicio del timeout
                startTimeout = System.nanoTime();

                // Se establece el timeout y se espera a recibir el ACK
                try {
                    readyChannels = selector.select(RTO);
                } catch (IOException e) {
                    System.out.println("Error en el selector");
                }

            } else {

                // Si no hay ACK pendiente, se comprueba si el canal está listo para leer (más
                // prioridad que escribir)
                try {
                    readyChannels = selector.selectNow();
                } catch (IOException e) {
                    System.out.println("Error en el selector");
                }
            }

            // Si el canal no está listo para leer, se envía un paquete
            if (readyChannels == 0) {

                // Se comprueba si se va a enviar el último paquete
                if (fileBytes.length <= to) {
                    to = fileBytes.length;
                    EOF = true;
                }

                // Se obtienen los siguientes 1460 bytes (salvo en la última transmisión)
                dataBytes = Arrays.copyOfRange(fileBytes, from, to);

                // Se crea la cabecera de aplicación + datos (destIP[4] + destPort[2] +
                // sequenceNumber[2] + RTT[4] + data[1460] = 1472 bytes)
                toSendBuffer = ByteBuffer.allocate(dataBytes.length + 12);
                toSendBuffer.put(bytesIP);
                toSendBuffer.put(bytesPort);
                toSendBuffer.putShort(sequenceNumber);
                RTT = (int) (System.nanoTime() / 1000000);
                toSendBuffer.putInt(RTT);
                toSendBuffer.put(dataBytes);
                // Se prepara el buffer para ser enviado (leído)
                toSendBuffer.flip();

                // Se envía el paquete al router
                try {
                    channel.send(toSendBuffer, router);
                } catch (IOException e) {
                    System.out.println("Error al enviar el paquete");
                    return;
                }

                // Se indica que hay un ACK pendiente
                pendingACK = true;

                continue;
            }

            // Si el canal está listo para leer, se lee el ACK entrante
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {

                SelectionKey key = keyIterator.next();

                if (key.isReadable()) {

                    // Se espera al ACK
                    do {

                        // Si hay un canal listo, se lee el ACK
                        if (readyChannels == 1) {

                            // Se limpia el buffer para el nuevo ACK
                            ACK.clear();

                            // Se recibe el paquete
                            try {
                                channel.receive(ACK);
                            } catch (IOException e) {
                                System.out.println("Error en la recepción del paquete");
                            }

                            // Se comprueba si el número de secuencia es el esperado
                            if (ACK.getShort(6) != sequenceNumber) {

                                // Se obtiene el tiempo actual
                                currentTime = System.nanoTime();

                                // Se resta al RTO el tiempo transcurrido desde el envío del paquete con datos
                                RTO = RTO - (int) ((currentTime - startTimeout) / 1000000);

                                // Si el RTO queda muy próximo a cero (timeout infinito) se aumenta ligeramente
                                if (RTO <= 0) {
                                    RTO = 5;
                                }

                                // Se sigue esperando otro ACK el tiempo restante
                                try {
                                    readyChannels = selector.select(RTO);
                                } catch (IOException e) {
                                    System.out.println("Error en el timeout");
                                }
                            }

                            // Se marca que no se debe retransmitir
                            rtx = false;

                        } else {

                            // Si no hay ningún canal listo para ser leído, se marca que se debe
                            // retransmitir
                            rtx = true;
                            // Se establece el RTO al último estable
                            RTO = previousRTO;

                            break;
                        }

                        // Se vuelve a comprobar si llegó otro ACK
                    } while (ACK.getShort(6) != sequenceNumber);

                    // Se indica que no hay ningún ACK pendiente
                    pendingACK = false;

                    // Si hay que retransmitir, se reinicia el selector y se pasa a retransmitir
                    if (rtx) {
                        keyIterator.remove();
                        continue;
                    }

                    // Se comprueba si se terminó de transmitir el fichero
                    if (EOF) {
                        // Se cierra el socket
                        try {
                            channel.close();
                        } catch (IOException e) {
                            System.out.println("Error al cerrar el canal");
                        }

                        // Se muestra el tiempo de ejecución
                        System.out.println(
                                "\nTiempo de ejecución: " + ((System.nanoTime() - startTime) / 1000000000) + "s");
                        return;
                    }

                    // Se calcula el próximo RTO
                    RTT = (int) (System.nanoTime() / 1000000 - ACK.getInt(8));
                    SRTT = (0.875) * previousSRTT + 0.125 * RTT;
                    devRTT = (0.75) * previousDevRTT + 0.25 * Math.abs(SRTT - RTT);
                    RTO = (int) (SRTT + 4 * devRTT);

                    // Se actualizan los offsets, el número de secuencia y las medidas para el RTO
                    sequenceNumber++;
                    from += 1460;
                    to += 1460;
                    previousSRTT = SRTT;
                    previousDevRTT = devRTT;
                    previousRTO = RTO;

                }

                // Se reinicia el selector
                keyIterator.remove();
            }

        } while (true);
    }
}