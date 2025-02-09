# UDP File Transmission
*UDP File Transmission* is a **Reliable UDP File Transfer Protocol** developed as part of the course "[Redes de Ordenadores](https://secretaria.uvigo.gal/docnet-nuevo/guia_docent/?centre=305&ensenyament=V05G301V01&assignatura=V05G301V01210&any_academic=2020_21)" in the Telecommunications Engineering Degree at the Universidad de Vigo (2020 - 2021).

## About The Project
This project implements a reliable file transfer protocol over UDP, demostrating error handling, packet loss recovery, and retrasmission mechanisms. The system integrates a client-server architecture with an intermediary router that introduces controlled packet loss and delay to simulate real network conditions.

The project features:
- File transmission over UDP with a custom reliability mechanism.
- A client that sends a file to a server.
- A router that simulates network delay and packet loss.
- A server that receives and reconstructs a file.
- Sliding window protocol for efficient and reliable data transfer.
- Performance analysis through recorded transmission timings.

## How To Run
### Compilation
Make sure you have a [Java JDK](https://www.oracle.com/java/technologies/downloads/) installed on your system. Then compile all Java classes and generate the `.class` files with:

```bash
javac -d bin src/*.java
```

This command creates the compiled files inside the `bin/` directory.

### Execution
Once compiled, you can run the system with:
#### Server
```bash
java -cp bin ro2021recv <output_file> <port>
```

| Option | Description | Example |
|--------|-------------|---------|
| `output_file` | File where received data will be stored | `output.png` |
| `port` | Port where the server listens | `5000` |

##### Example
```bash
java -cp bin ro2021recv output.png 5000
```

#### Router
```bash
./shufflerouter.exe [-d drop_rate] [-m min_delay] [-r rand_delay] [-p port]
```
| Option | Description | Example |
|--------|-------------|---------|
| `-d drop_rate` | Probability to drop packets | `-d 0.1` |
| `-m min_delay` | Minimum transmission delay in milliseconds | `-m 5` |
| `-r rand_delay` | Maximum transmission delay in milliseconds | `-r 15` |
| `-p port` | Port where the router listens | `-p 6000` |

##### Example
```bash
./shufflerouter.exe -d 0.1 -m 5 -r 15 -p 6000
```

#### Client
```bash
java -cp bin ro2021send <input_file> <server_ip> <server_port> <router_ip> <router_port>
```
| Option | Description | Example |
|--------|-------------|---------|
| `input_file` | File to be sent | `test.png` |
| `server_ip` | Server IP address | `127.0.0.1` |
| `server_port` | Port where the server is listening | `5000` |
| `router_ip` | Router IP address | `127.0.0.1` |
| `router_port` | Port where the router is listening | `6000` |

##### Example
```bash
java -cp bin ro2021send test/test.png 127.0.0.1 5000 127.0.0.1 6000
```

## About The Code
Refer to [`Especificaciones.pdf`](docs/Especificaciones.pdf) and [`Implementación.pdf`](docs/Implementación.pdf) for an in-depth explanation of the project, the protocol implementation, how the system works, how to run it, and more.

Refer to [`Resultados.ods`](docs/Resultados.ods) for information on achieved results.
