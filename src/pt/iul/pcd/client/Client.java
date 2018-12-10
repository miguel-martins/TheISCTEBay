package pt.iul.pcd.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.iul.pcd.directory.Directory;
import pt.iul.pcd.gui.GUI;
import pt.iul.pcd.message.Block;
import pt.iul.pcd.message.FileDetails;
import pt.iul.pcd.message.FileResponse;
import pt.iul.pcd.user.User;

public class Client {

	//Constante relativa ao tamanho m�ximo de um bloco em bits
	public static final int MAX_BLOCK_LENGHT = 2;
	// Atributos relativos aos detalhes de execu��o
	private String directoryAddress;
	private int directoryPort;
	private int clientPort;
	private String fileFolder;
	// Atributos relativos � conex�o e comunica��o com o diret�rio
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	// Atributos relativos ao servidor e comunica��o com outros utilizadores
	private ServerSocket serverSocket;
	private ObjectOutputStream outToClient;
	private ObjectInputStream inFromClient;
	// Atributo relativo ao interface gr�fico do utilizador
	private GUI gui;
	// Ficheiros
	File[] files;
	// HashMap
	private Map<FileDetails, ArrayList<User>> peerList;

	public Client(String[] args) {
		try {
			loadGUI();
			loadFields(args);
			initializeConnection();
			logIn();
			initializeServer();
//			communicateWithDirectory();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// Inicializar o interface gr�fico do utilizador
	private void loadGUI() {
		gui = new GUI(this);
	}

	// Detalhes de execu��o
	private void loadFields(String[] args) {
		if (args != null) {
			directoryAddress = args[0];
			directoryPort = Integer.parseInt(args[1]);
			clientPort = Integer.parseInt(args[2]);
			fileFolder = args[3];
			files = new File(fileFolder).listFiles();	
		}
	}

	// Liga��o ao diret�rio atrav�s da socket e cria��o dos canais de comunica��o
	private void initializeConnection() throws IOException {
		socket = new Socket(directoryAddress, directoryPort);
		out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

	}

	// Enviar mensagem ao diretorio e inscrever o utilizador
	private void logIn() throws UnknownHostException {
		out.println("INSC " + InetAddress.getByName(null).toString().split("/")[1] + " " + clientPort);
	}

	private void initializeServer() {
		try {
			serverSocket = new ServerSocket(clientPort);
			while (true) {
				Socket incomingConnection = serverSocket.accept();
				System.out.println("Nova conexao");
				new ClientConnectionThread(incomingConnection, this).start();

			}
		} catch (IOException e) {
			System.out.println("Cliente saiu da conex�o P2P");
		}
	}

	// Procurar no diret�rio por ficheiro com a palavra chave dada
	public void searchKeyword(String keyword) {
		try {
			peerList = new HashMap<FileDetails, ArrayList<User>>();
			List<User> userInfo = getUserInfo();
			if (!userInfo.isEmpty()) {
				for (User info : userInfo) {
					System.out.println("Percorrer os userPorts e criar InquiryThreads");
					Socket socket = new Socket(info.getUserAddress(), info.getUserPort());
					InquiryThread iq = new InquiryThread(socket, keyword, this, info);
					iq.start();
					iq.join();
				}
			} else {
				System.out.println("Client - Lista Vazia");
			}
		} catch (IOException e) {
			System.out.println("INQUIRY THREAD EXCEP��O");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	private List<User> getUserInfo() throws IOException, InterruptedException {
		List<User> userInfo = new ArrayList<User>();
		out.println(Directory.CONSULT);
		while (true) {
			String message = in.readLine();
			if (!message.equals(Directory.END_CONSULT)) {
				String userAddress = message.split(" ")[1];
				String userPort = message.split(" ")[2];
				if (Integer.parseInt(userPort) != clientPort) {
					User user = new User(userAddress, Integer.parseInt(userPort));
					userInfo.add(user);

				}
			} else
				break;
		}
		return userInfo;
	}
	
	public void updateFileList(FileResponse fileResponse, User user) {
		addToPeerList(fileResponse, user);
	}

	private void addToPeerList(FileResponse fileResponse, User user) {
		for (FileDetails fd : fileResponse.getList()) {
			if(peerList.containsKey(fd))
				peerList.get(fd).add(user);
			else
			{
				List<User> list = new ArrayList<User>();
				list.add(user);
				peerList.put(fd, (ArrayList<User>) list);
			}
		}
	}
	
	public FileResponse getPeerList()
	{
		FileResponse fp = new FileResponse();
		for(FileDetails fd: peerList.keySet())
		{
			fp.addFileDetails(fd);
		}
		return fp;
	}
	
	public FileResponse searchForFile(String fileName) {
		FileResponse fileResponse = new FileResponse();
		for (int i = 0; i != files.length; i++) {
			if (files[i].getName().contains(fileName)) {
				fileResponse.addFileDetails(new FileDetails(files[i].getName(), files[i].length()));
			}

		}
		return fileResponse;
	}
	
	private List<User> getUsersWithFileDetails(FileDetails fileDetails)
	{
		return peerList.get(fileDetails);
	}

	//Se o utilizador tentar ligar-se a um utilizador que j� nao existe, tratar desse caso. O utilizador podia ter estado online quando disse que tinha
	//o ficheiro procurado, mas entretanto desligou-se.
}
