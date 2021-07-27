/**
 * Servidor
 * @author Luan Lima RA 11047716
 * Sistemas Distribuidos - Q2 2021
 */

package project;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// Biblioteca externa
import com.google.gson.*; //2.8.7

public class Servidor {
	private static ConcurrentHashMap<String, String> mapList;
	private static ConcurrentHashMap<String, Boolean> mapState;
	private static DatagramSocket socket;
	private DatagramPacket packet;
	
	public Servidor() throws SocketException {
		// Inicia uma estrutura de dados para armazenar a INFORMACAO dos peers, ou seja,
		// sua lista de arquivos
		// Key: peerID -> ip:port
		// Value: lista de arquivos do peer
		mapList = new ConcurrentHashMap<>();
		// Inicia uma estrutura de dados para armazenar o ESTADO dos peers, ou seja, se
		// estao conectados ou nao
		// Key: peerID -> ip:port
		// Value: true (conectado) ou false (desconectado)
		mapState = new ConcurrentHashMap<>();
		// Criacao do Socket UDP na porta 10098
		socket = new DatagramSocket(10098);
		// Criacao do datagrama, preenchendo o buffer com as infos recebidas
        byte[] dataRx = new byte[1024];
        packet = new DatagramPacket(dataRx, dataRx.length);
	}

	public DatagramPacket getPacket() {
		return packet;
	}

	public static void main(String[] args) throws Exception {
		/**
		 * Inicializacao do Servidor: cria estrutura de dados para armazenar arquivos e
		 * socket UDP para responder solicitacoes.
		 */
		Servidor server = new Servidor();
		
		System.out.println("######################################");
		System.out.println("# Servidor inicializado com sucesso! #");
		System.out.println("######################################");
		
		while (true) {
			/**
			 * Aguarda o recebimento de um novo packet
			 */
            socket.receive(server.getPacket()); // Bloqueante
            
			/**
			 * Processando datagrama UDP do Client: byte -> String JSON -> Java Obj
			 */
            Mensagem msg = null;
            try {
            	 // Transformando o datagrama de bytes para String
                String requestJson = new String(server.getPacket().getData(), server.getPacket().getOffset(), server.getPacket().getLength());

                // Fazendo um parse de JSON para objeto Java
                Gson gson = new Gson();
                msg = gson.fromJson(requestJson, Mensagem.class);

            } catch (Exception e) {
                System.err.println("Erro ao receber Datagrama UDP!");
            }
    
			/**
			 * ThreadReply: a thread abaixo eh criada APENAS quando um novo datagrama eh
			 * recebido. Dentro da ThreadReply sao respondidas as requisicoes do peer e
			 * tambem eh instanciada a thread de tratamento do ALIVE, sendo o JOIN o trigger
			 * (evento gerador unico) da segunda Thread.
			 */
			ThreadReply threadReply = new ThreadReply(server.getPacket().getAddress(), server.getPacket().getPort(), msg);
			threadReply.start();
		}
	}
	

	private static class ThreadReply extends Thread {
        private InetAddress peerIpUDP;
        private int peerPortUDP;
        private String method;
        private String payload;
        private String peerIpTCP;
        private int peerPortTCP;
        private String peerID;
        

        public ThreadReply(InetAddress peerIpUDP, int peerPortUDP, Mensagem msg) {
            this.peerIpUDP = peerIpUDP;
            this.peerPortUDP = peerPortUDP; 
            method = msg.getMethod();
            payload = msg.getPayload();
            peerIpTCP = msg.getIp();
            peerPortTCP = msg.getPort();
            peerID = msg.getId();
        }
        
        public void run() {
            /**
             * Resposta do Server para o Client
             */
            if (method.equals("JOIN")) {
            	
            	// Checa se o peer caiu sem avisar e retornou antes do ALIVE
            	if(!mapList.containsKey(peerID)) {
            		// Acessa os hashmaps e adiciona o peer
            		mapList.put(peerID, payload);
            		mapState.put(peerID, true);            		
            	} else {
            		// Se sim, apenas atualiza a lista e o estado do peer
            		mapList.replace(peerID, payload);
            		mapState.replace(peerID, true);
            	}
            	
                // Interacao com o usuario: Mensagem confirmando recebimento do JOIN
                System.out.println("Peer " + peerIpTCP + ":" + peerPortTCP + " adicionado com arquivos " + mapList.get(peerID));
                
                // Resposta: JOIN_OK
                Mensagem joinOk = new Mensagem("JOIN_OK");
    
                // Transforma o objeto em uma String JSON
                Gson gson = new Gson();
                String joinOkJson = gson.toJson(joinOk);
                try {
                    UDPsend(peerIpUDP, peerPortUDP, joinOkJson);
                } catch (Exception e) {
                    System.err.println("Erro ao enviar JOIN_OK!");
                }
                                
				/**
				 * ThreadAlive: sempre que um peer inicia uma conexao com JOIN uma nova thread
				 * que trata de verificar se o peer continua na rede eh criada.
				 */
                ThreadAlive threadAlive = new ThreadAlive(peerID, peerIpTCP, peerPortTCP, peerIpUDP, peerPortUDP);
    			threadAlive.start();
    
            } else if (method.equals("LEAVE")) {
            	// Acessa hashmap e remove o peer
            	mapList.remove(peerID);
            	mapState.remove(peerID);
            	
                // Resposta: LEAVE_OK
                Mensagem leaveOk = new Mensagem("LEAVE_OK");
    
                // Transforma o objeto em uma String JSON
                Gson gson = new Gson();
                String leaveOkJson = gson.toJson(leaveOk);
                try {
                    UDPsend(peerIpUDP, peerPortUDP, leaveOkJson);
                } catch (Exception e) {
                    System.err.println("Erro ao enviar LEAVE_OK!");
                }
            	
            } else if (method.equals("SEARCH")) {            	
            	System.out.println("Peer " + peerIpTCP + ":" + peerPortTCP + " solicitou o arquivo " + payload);

            	// Cria arraylist para armazenar o id de todos os peers que contem o arquivo
            	ArrayList<String> resultList = new ArrayList<String>();

            	// Percorre a lista de arquivos de cada peer contido no mapList
            	// Adaptado de: https://stackoverflow.com/questions/7347856/how-to-convert-a-string-into-an-arraylist
            	for(Map.Entry<String, String> entry : mapList.entrySet()) {
            		// Captura os valores do peer
            		String arrayStr = mapList.get(entry.getKey());
            		// Formatando a string recebida para transformar em arraylist
            		String replacebracket1 = arrayStr.replace("[", "");
            		String replacebracket2 = replacebracket1.replace("]", "");
            		String peerStr = replacebracket2.replace(" ", "");
            		// Criacao do arraylist do peer
            		ArrayList<String> peerList = new ArrayList<String>(Arrays.asList(peerStr.split(",")));
					// Se o arquivo estiver contido no arraylist do peer, adiciona na lista de
					// resultado que sera enviada ao client
            		if(peerList.contains(payload) && !entry.getKey().equals(peerID)) {
            			resultList.add(entry.getKey());
            		}
            	}
            	        		
        		// Transforma a lista em uma string
        		String resultStr = resultList.toString();
        		
        		Mensagem searchOk = new Mensagem("SEARCH", resultStr);               
    
                // Transforma o objeto em uma String JSON
                Gson gson = new Gson();
                String searchOkJson = gson.toJson(searchOk);
                
                // Envia o datagrama UDP ao client
                try {
                    UDPsend(peerIpUDP, peerPortUDP, searchOkJson);
                } catch (Exception e) {
                    System.err.println("Erro ao enviar SEARCH!");
                }
                
            } else if (method.equals("UPDATE")) {
            	// Acessa hashmap, remove e adiciona novamente o peer
            	mapList.remove(peerID);
            	mapList.put(peerID, payload);
            	
                // Resposta: UPDATE_OK
                Mensagem updateOk = new Mensagem("UPDATE_OK");
    
                // Transforma o objeto em uma String JSON
                Gson gson = new Gson();
                String updateOkJson = gson.toJson(updateOk);
                try {
                    UDPsend(peerIpUDP, peerPortUDP, updateOkJson);
                } catch (Exception e) {
                    System.err.println("Erro ao enviar UPDATE_OK!");
                }
    
            } else if (method.equals("ALIVE_OK")) {

            	mapState.replace(peerID, true);
            	
            }
        }
            
        static void UDPsend(InetAddress IPAddress, int port, String msgJson) throws Exception {
            // Declara buffer vazio de 1024 bytes para recebimento
            byte[] dataTx = new byte[1024];
    
            // Preenche buffer com os bytes da mensagem a ser enviada
            dataTx = msgJson.getBytes();
    
            // Criacao do datagrama com endereco e porta do peer
            DatagramPacket packetTx = new DatagramPacket(dataTx, dataTx.length, IPAddress, port);
    
            // Envio do datagrama ao peer
            socket.send(packetTx);
        }
    }
	
	private static class ThreadAlive extends Thread {
		private String peerID;
		private String peerIpTCP;
		private int peerPortTCP;
		private InetAddress peerIpUDP;
		private int peerPortUDP;
        private boolean newConn;
        private int SLEEP_TIME = 30000;
        
        public ThreadAlive(String peerID, String peerIpTCP, int peerPortTCP, InetAddress peerIpUDP, int peerPortUDP) {
            this.peerID = peerID;
            this.peerIpTCP = peerIpTCP;
            this.peerPortTCP = peerPortTCP;
            this.peerIpUDP = peerIpUDP;
            this.peerPortUDP = peerPortUDP;
            newConn = true;
        }
        
        public void run() {
			// Executa enquanto o peer estiver no hashmap (pode ser removido por requisicao
			// LEAVE do peer, tratada na ThreadReply)
        	while(mapList.containsKey(peerID)) {
        		// Se for a primeira conexao, dorme por 30s
        		if(newConn) {
        			try {
        				newConn = false;
						Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
        		} else {
        			mapState.replace(peerID, false);
        			/**
                     * Requisicao ALIVE do Server para o Client
                     */
                    Mensagem alive = new Mensagem("ALIVE");
        
                    // Transforma o objeto em uma String JSON
                    Gson gson = new Gson();
                    String aliveJson = gson.toJson(alive);
        			try {
        				// Declara buffer vazio de 1024 bytes para recebimento
                        byte[] dataTx = new byte[1024];
                
                        // Preenche buffer com os bytes da mensagem a ser enviada
                        dataTx = aliveJson.getBytes();
                
                        // Criacao do datagrama com endereco e porta UDP do peer
                        DatagramPacket packetTx = new DatagramPacket(dataTx, dataTx.length, peerIpUDP, peerPortUDP);

                        // Envio do datagrama ao peer
                        socket.send(packetTx);
						
					} catch (Exception e) {
						System.err.println("Erro ao enviar ALIVE!");
					}
					/**
					 * Janela de tempo de 1s para aguardar a resposta do client. A resposta sera
					 * recebida na ThreadReply e o estado (value) do peer (key peerID) no hashmap
					 * global mapState sera atualizado pela thread.
					 */
        			try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					/**
					 * Checa o estado (conectado/desconectado) do Client, ou seja, se recebeu o
					 * ALIVE_OK no main do Servidor.
					 */
					// Se true, peer respondeu ALIVE_OK durante a janela de 1s
        			if(!mapState.get(peerID)) {
						// Removendo peer dos hashmaps de info e estado, consequentemente saindo da
						// condicao de loop infinito da Thread.
        				System.out.println("Peer " + peerIpTCP + ":" + peerPortTCP + " morto. Eliminando seus arquivos " + mapList.get(peerID));
        				mapState.remove(peerID);
        				mapList.remove(peerID);
        			}
        			
        			try {
						Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
        			
        		}
        	}
        }		
	}	
}

