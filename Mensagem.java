/**
 * Mensagem
 * @author Luan Lima RA 11047716
 * Sistemas Distribuidos - Q2 2021
 */

package project;

public class Mensagem {
	
	private String method;
	private String payload;
	private String ip;
	private int port;
	private String id;
	
	// Requisicoes/respostas Peer JOIN, LEAVE, SEARCH, UPDATE e ALIVE_OK
	public Mensagem(String met, String pay, String ip, int port) {
		method = met;
		payload = pay;
		this.ip = ip;
		this.port = port;
		id = ip + ":" + port;
	}
	
	// Respostas Server SEARCH com arquivos
	// Requisicao Peer DOWNLOAD
	public Mensagem(String request, String pay) {
		method = request;
		payload = pay;
	}
	
	// Respostas Server JOIN_OK, LEAVE_OK, UPDATE_OK, ALIVE e SEARCH sem arquivos
	// Resposta Peer DOWNLOAD_NEGADO (TCP)
	public Mensagem(String request) {
		method = request;
	}

	public String getMethod() {
		return method;
	}

	public String getPayload() {
		return payload;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public String getId() {
		return id;
	}

}
