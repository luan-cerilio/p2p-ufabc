/**
 * Peer
 * @author Luan Lima RA 11047716
 * Sistemas Distribuidos - Q2 2021
 */

package project;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
// Biblioteca externa
import com.google.gson.*; //2.8.7

public class Peer {
	private static String JOIN = "1";
	private static String SEARCH = "2";
	private static String DOWNLOAD = "3";
	private static String LEAVE = "4";
	private static ArrayList<String> searchList;
	private static String fileToDownload;

	private String IP;
	private int port;
	private String files;
	private String path;
	private boolean connected;
	private DatagramSocket socketUDP;
	private ServerSocket serverSocketTCP;

	public Peer(String ipPeer, int portTCP, String filesPeer, String pathPeer) throws IOException {
		IP = ipPeer;
		port = portTCP;
		files = filesPeer;
		path = pathPeer;
		socketUDP = new DatagramSocket();
		serverSocketTCP = new ServerSocket(portTCP);
		searchList = new ArrayList<String>();
		connected = false;
		fileToDownload = null;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public DatagramSocket getSocketUDP() {
		return socketUDP;
	}

	public String getFiles() {
		return files;
	}

	public void setFiles(String files) {
		this.files = files;
	}

	public String getIP() {
		return IP;
	}

	public int getPort() {
		return port;
	}

	public ServerSocket getServerSocketTCP() {
		return serverSocketTCP;
	}

	public String getPath() {
		return path;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		/**
		 * Inicializacao do Peer. Capturando informacoes de IP, portas UDP, TCP e
		 * caminho dos arquivos.
		 */
		System.out.println("####################################");
		System.out.println("####### Inicializando Peer... ######");
		System.out.println("####################################");

		// Buffer para leitura do teclado
		BufferedReader inputBuf = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Digite o IP do Peer:");
		String ipStr = inputBuf.readLine(); // Bloqueante
		InetAddress address = InetAddress.getByName(ipStr);
		String ipPeer = address.getHostAddress();
		System.out.println("Digite a porta TCP do Peer:");
		int portPeer = Integer.parseInt(inputBuf.readLine()); // Bloqueante
		System.out.println("Digite a pasta do Peer:");
		String path = inputBuf.readLine(); // Aguarda input
		// Vai ate a pasta e obtem a lista de arquivos
		String filesPeer = getFiles(path);

		/**
		 * Instanciando a classe Peer. Parametros: IP do peer, porta TCP do peer,
		 * caminho e lista de arquivos.
		 */
		Peer peer = new Peer(ipPeer, portPeer, filesPeer, path);

		/**
		 * Inicia a Thread de servidor TCP
		 */
		ThreadTCPServer threadTCPServer = new ThreadTCPServer(peer);
		threadTCPServer.start();

		/**
		 * Inicia Thread para tratar as respostas e requisicao ALIVE vindas do servidor
		 */
		ThreadUDPServer threadUDPServer = new ThreadUDPServer(peer);
		threadUDPServer.start();

		System.out.println("\n######################################");
		System.out.println("### Peer inicializado com sucesso! ###");
		System.out.println("######################################");

		while (true) {
			/**
			 * Menu interativo. Opcoes: JOIN, SEARCH, DOWNLOAD e LEAVE. As opcoes sao
			 * representadas por numeros inteiros.
			 */
			System.out.println("Selecione: (1) JOIN (2) SEARCH (3) DOWNLOAD (4) LEAVE");
			String selec = inputBuf.readLine(); // Bloqueante

			if (selec.equals(JOIN) && !peer.isConnected()) {

				// Instanciando a classe Mensagem
				Mensagem msg = new Mensagem("JOIN", filesPeer, peer.getIP(), peer.getPort());

				// Transformando o objeto em uma String JSON
				Gson gson = new Gson();
				String msgJson = gson.toJson(msg);

				// Instanciando ThreadUDPClient
				ThreadUDPClient threadJoin = new ThreadUDPClient(peer, msgJson);
				threadJoin.start();
				peer.setConnected(true);

			} else if (selec.equals(SEARCH) && peer.isConnected()) {
				/**
				 * SEARCH: Solicita o arquivo ao usuario, armazena o arquivo solicitado na
				 * variavel global fileToDownload, cria a Mensagem que sera enviada como
				 * Datagrama UDP e inicia a Thread que faz o envio do Datagrama (client UDP)
				 */

				System.out.println("Digite o nome do arquivo:");
				String file = inputBuf.readLine(); // Bloqueante
				fileToDownload = file;

				// Instanciando a classe Mensagem
				Mensagem msg = new Mensagem("SEARCH", file, peer.getIP(), peer.getPort());

				// Transformando o objeto em uma String JSON
				Gson gson = new Gson();
				String msgJson = gson.toJson(msg);

				// Instanciando ThreadUDPClient
				ThreadUDPClient threadSearch = new ThreadUDPClient(peer, msgJson);
				threadSearch.start();

			} else if (selec.equals(DOWNLOAD) && peer.isConnected()) {
				/**
				 * DOWNLOAD: verifica se foi feito um search antes, inicia conexao TCP e fica em
				 * loop ate conseguir realizar o download do arquivo
				 */
				
				if (!searchList.toString().equals("[]")) {
					boolean downAck = false;
					while (!downAck) {

						for (int i = 0; i < searchList.size(); i++) {
							/**
							 * Envio da solicitacao do arquivo - TCP
							 */
							// Separa IP e porta
							String[] infoTCPServer = searchList.get(i).split(":");
							String ipTCPServer = infoTCPServer[0];
							int portTCPServer = Integer.parseInt(infoTCPServer[1]);

							// Tenta criar uma conexao com o Peer Server TCP
							Socket socketTCP = new Socket(ipTCPServer, portTCPServer);

							// Cria a cadeia de saida (escrita) de informacoes no socket
							OutputStream outStream = socketTCP.getOutputStream();
							DataOutputStream outWriter = new DataOutputStream(outStream);

							// Instanciando a classe Mensagem
							Mensagem msg = new Mensagem("DOWNLOAD", fileToDownload);

							// Transformando o objeto em uma String JSON
							Gson gson = new Gson();
							String msgJson = gson.toJson(msg);

							// Escrita no socket (envio do pacote TCP)
							outWriter.writeBytes(msgJson + "\n");

							/**
							 * Recebendo a resposta TCP
							 */

							// Cria a cadeia de entrada (leitura) de informacoes vindas do socket
							InputStreamReader inpStream = new InputStreamReader(socketTCP.getInputStream());							
							BufferedReader buffReader = new BufferedReader(inpStream);
							
							// Aguarda a resposta do Server
							String msgRxJson = buffReader.readLine(); // Bloqueante
							
							// Fazendo um parse de JSON para objeto Java
							Mensagem msgRx = gson.fromJson(msgRxJson, Mensagem.class);

							if (msgRx.getMethod().equals("DOWNLOAD")) {
								
								// Adaptado de: https://gist.github.com/CarlEkerot/2693246
								// Modificacoes feitas: o tamanho do arquivo eh enviado pelo outro peer antes do
								// download e, alem disso, eh do tipo long, para suportar arquivos maiores que
								// 2.147.483.648 bytes 
								String filePath = peer.getPath() + "\\" + fileToDownload;
								File file = new File(filePath);
								DataInputStream dis = new DataInputStream(socketTCP.getInputStream());
								FileOutputStream fos = new FileOutputStream(file);
								byte[] buffer = new byte[4096];

								long fileSize = Long.parseLong(msgRx.getPayload());
								int read = 0;
								long remaining = fileSize;

								while((read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
									remaining -= read;
									fos.write(buffer, 0, read);
								}

								socketTCP.close();
								fos.close();
								dis.close();
								downAck = true;
								System.out.println("Arquivo " + fileToDownload + " baixado com sucesso na pasta " + peer.getPath());
								break;
								
							} else {
								
								// Se estiver no ultimo da lista, solicitara ao primeiro novamente
								if(i == searchList.size() - 1) {
									
									// Separa IP e porta
									String[] infoFirst = searchList.get(0).split(":");
									String ipFirst = infoFirst[0];
									int portFirst = Integer.parseInt(infoFirst[1]);
									
									System.out.println("peer " + ipTCPServer + ":" + portTCPServer + " negou o download, pedindo agora para o peer " + ipFirst + ":" + portFirst);
									
								} else {
									
									// Separa IP e porta
									String[] infoNext = searchList.get(i+1).split(":");
									String ipNext = infoNext[0];
									int portNext = Integer.parseInt(infoNext[1]);
									
									System.out.println("peer " + ipTCPServer + ":" + portTCPServer + " negou o download, pedindo agora para o peer " + ipNext + ":" + portNext);
									
								}
								socketTCP.close();
							}
						}
						// Aguarda um tempo para solicitar o arquivo novamente
						TimeUnit.MILLISECONDS.sleep(1500);
					}
					/**
					 * UPDATE: atualiza lista de arquivos do peer e envia novo Datagrama UDP ao
					 * Server
					 */
					peer.setFiles(getFiles(peer.getPath()));
					
					// Instanciando a classe Mensagem
					Mensagem msg = new Mensagem("UPDATE", filesPeer, peer.getIP(), peer.getPort());

					// Transformando o objeto em uma String JSON
					Gson gson = new Gson();
					String msgJson = gson.toJson(msg);

					// Instanciando a classe ThreadUDPClient
					ThreadUDPClient threadUpdate = new ThreadUDPClient(peer, msgJson);
					threadUpdate.start();
					
				} else {
					System.err.println("Nao ha arquivos para DOWNLOAD!");
				}

			} else if (selec.equals(LEAVE)) {

				// Instanciando a classe Mensagem
				Mensagem msg = new Mensagem("LEAVE", null, peer.getIP(), peer.getPort());

				// Transformando o objeto em uma String JSON
				Gson gson = new Gson();
				String msgJson = gson.toJson(msg);

				// Instanciando a classe ThreadUDPClient
				ThreadUDPClient threadLeave = new ThreadUDPClient(peer, msgJson);
				threadLeave.start();
				peer.setConnected(false);

			} else {
				System.err.println("Opcao invalida!");
			}
			// Aguarda um tempo para mostrar o menu novamente, evitando que as mensagens
			// de resposta do servidor baguncem o menu
			TimeUnit.MILLISECONDS.sleep(400);
		}
	}

	public static String getFiles(String path) {
		// Lista os arquivos contidos no diretorio
		File folder = new File(path);
		File[] listOfFiles = folder.listFiles();
		// Fonte:
		// https://stackoverflow.com/questions/5694385/getting-the-filenames-of-all-files-in-a-folder

		// Cria lista com os arquivos contidos no diretorio
		ArrayList<String> list = new ArrayList<String>();
		for (int i = 0; i < listOfFiles.length; i++)
			list.add(listOfFiles[i].getName());

		return list.toString();
	}

	private static class ThreadUDPClient extends Thread {

		private String msgJson;
		private DatagramSocket socketUDP;

		public ThreadUDPClient(Peer peer, String msg) throws SocketException {
			msgJson = msg;
			socketUDP = peer.getSocketUDP();
		}

		public void run() {
			/**
			 * Envio da requisicao UDP ao Server
			 */
			try {
				// IP do server (fixo)
				InetAddress IPAddress = InetAddress.getByName("127.0.0.1");

				// Declara buffer vazio
				byte[] msg = new byte[1024];

				// Preenche buffer com os bytes da mensagem a ser enviada
				msg = msgJson.getBytes();

				// Criacao do datagrama com endereco e porta do server
				DatagramPacket packetTx = new DatagramPacket(msg, msg.length, IPAddress, 10098);

				// Envio do datagrama ao server
				socketUDP.send(packetTx);
			} catch (Exception e) {
				System.err.println("Erro ao ENVIAR Datagrama!");
			}
		}
	}

	private static class ThreadUDPServer extends Thread {

		private Peer peer;

		public ThreadUDPServer(Peer peer) {
			this.peer = peer;
		}

		public void run() {

			// Declara buffer vazio de 1024 bytes para recebimento
			byte[] dataRx = new byte[1024];
			// Criacao do datagrama de recebimento, preenchendo o buffer com as infos
			// recebidas
			DatagramPacket packetRx = new DatagramPacket(dataRx, dataRx.length);

			while (true) {
				/**
				 * Aguarda resposta ou requisicao do servidor
				 */
				try {
					peer.getSocketUDP().receive(packetRx); // Bloqueante
				} catch (IOException e) {
					System.err.println("Erro ao RECEBER Datagrama!");
				}

				// Transformando o datagrama de bytes para String
				String msgRxJson = new String(packetRx.getData(), packetRx.getOffset(), packetRx.getLength());

				// Fazendo um parse de JSON para objeto Java
				Gson gson = new Gson();
				Mensagem msgRx = gson.fromJson(msgRxJson, Mensagem.class);

				// Checando qual foi a resposta do servidor
				String method = msgRx.getMethod();

				if (method.equals("JOIN_OK")) {
					
					System.out.println(
							"Sou peer " + peer.getIP() + ":" + peer.getPort() + " com arquivos " + peer.getFiles());
					
				} else if (method.equals("LEAVE_OK")) {
					
					System.exit(0);
					
				} else if (method.equals("UPDATE_OK")) {
										
				} else if (method.equals("SEARCH")) {
					
					System.out.println("peers com arquivo solicitado:" + msgRx.getPayload());
					// Captura o array em formato String
					String arrayStr = msgRx.getPayload();
					// Formatando a string recebida para transformar em arraylist
					String replacebracket1 = arrayStr.replace("[", "");
					String replacebracket2 = replacebracket1.replace("]", "");
					String peerStr = replacebracket2.replace(" ", "");
					// Criacao do arraylist do resultado do search
					searchList = new ArrayList<String>(Arrays.asList(peerStr.split(",")));
					
				} else if (method.equals("ALIVE")) {
					
					// Instanciando a classe Mensagem: request - JOIN; payload - arquivos; identif -
					// peerID
					Mensagem msgTx = new Mensagem("ALIVE_OK", null, peer.getIP(), peer.getPort());

					// Transformando o objeto em uma String JSON
					String msgTxJson = gson.toJson(msgTx);

					// Instanciando a classe ThreadUDPClient
					try {
						ThreadUDPClient threadAliveOk = new ThreadUDPClient(peer, msgTxJson);
						threadAliveOk.start();
					} catch (SocketException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private static class ThreadTCPServer extends Thread {

		private ServerSocket serverSocketTCP;
		private String peerPath;

		public ThreadTCPServer(Peer peer) {
			serverSocketTCP = peer.getServerSocketTCP();
			peerPath = peer.getPath();
		}

		public void run() {
			while (true) {
				try {
					/**
					 * Aguarda nova conexao TCP e cria uma Thread que trata da requisicao de cada
					 * client separadamente
					 */
					Socket clientSocketTCP = serverSocketTCP.accept(); // Bloqueante
					ThreadTCPReqReply threadTCPReqReply = new ThreadTCPReqReply(clientSocketTCP, peerPath);
					threadTCPReqReply.start();

				} catch (IOException e) {
					System.err.println("Erro no handshake TCP!");
				}
			}
		}
	}

	private static class ThreadTCPReqReply extends Thread {

		private static String peerPath;
		private Socket clientSocketTCP;

		public ThreadTCPReqReply(Socket clientSocketTCP, String peerPath) {
			this.clientSocketTCP = clientSocketTCP;
			ThreadTCPReqReply.peerPath = peerPath;
		}

		public void run() {
			try {
				/**
				 * Recebe o pacote TCP do Client
				 */
				// Cria a cadeia de entrada (leitura) de informacoes do socket
				InputStreamReader inpStream = new InputStreamReader(clientSocketTCP.getInputStream());
				BufferedReader buffReader = new BufferedReader(inpStream);

				// Le a requisicao do client
				String msgRxJson = buffReader.readLine(); // Bloqueante

				/**
				 * Cria resposta randomica
				 */
				Random rand = new Random();
				
				// Para transformar de string json <-> objeto
				Gson gson = new Gson();
				
				if (rand.nextBoolean()) {
					/**
					 * Processando a mensagem para identificar o arquivo a ser enviado
					 */
					// Fazendo um parse de JSON para objeto Java
					Mensagem msgRx = gson.fromJson(msgRxJson, Mensagem.class);

					// Identificando o arquivo solicitado
					String filePath = peerPath + "\\" + msgRx.getPayload();
					
					File file = new File(filePath);
					String fileSize = String.valueOf(file.length());

					/**
					 * Criando a cadeia de saida do pacote TCP
					 */
					// Instanciando a classe Mensagem
					Mensagem msgTx = new Mensagem("DOWNLOAD", fileSize);

					// Transformando o objeto em uma String JSON
					String msgTxJson = gson.toJson(msgTx);

					// Cria a cadeia de saida (escrita) de informacoes do socket
					OutputStream outStream = (OutputStream) clientSocketTCP.getOutputStream();
					DataOutputStream outWriter = new DataOutputStream(outStream);
					
					// DataOutputStream outStream = new DataOutputStream(clientSocketTCP.getOutputStream());
					outWriter.writeBytes(msgTxJson + "\n");
				
					// Adaptado de: https://gist.github.com/CarlEkerot/2693246
					FileInputStream fis = new FileInputStream(filePath);
					byte[] buffer = new byte[4096];
					int read = 0;
					while((read = fis.read(buffer)) > 0) {
						outStream.write(buffer);
					}
					fis.close();
					outStream.close();
					outWriter.close();
					clientSocketTCP.close();
					

				} else {
					// Instanciando a classe Mensagem
					Mensagem msgTx = new Mensagem("DOWNLOAD_NEGADO");

					// Transformando o objeto em uma String JSON
					String msgTxJson = gson.toJson(msgTx);

					// Cria a cadeia de saida (escrita) de informacoes do socket
					DataOutputStream outStream = new DataOutputStream(clientSocketTCP.getOutputStream());
					outStream.writeBytes(msgTxJson + "\n");

				}

			} catch (Exception e) {
				System.err.println("Erro ao tratar a requisicao TCP!");
			}
		}
	}
}
