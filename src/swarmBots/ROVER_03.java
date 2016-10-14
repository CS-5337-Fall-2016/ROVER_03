package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

import common.Communication;
import common.Coord;
import common.MapTile;
import common.PlanetMap;
import common.ScanMap;
import enums.RoverDriveType;
import enums.Terrain;
import enums.RoverToolType;
import enums.Science;
import rover_logic.Astar;
import rover_logic.DStarLite;
import rover_logic.State;


/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_03 {

	BufferedReader in;
	PrintWriter out;
	String rovername;
	ScanMap scanMap;
	int sleepTime;
	String SERVER_ADDRESS = "localhost";

	ArrayList<String> radioactiveLocations = new ArrayList<String>();
	static final int PORT_ADDRESS = 9537;
	// Keep personal map when traversing - upload for each movement
	public static Map<Coord, MapTile> globalMap;
	char cardinals[] = { 'N', 'E', 'S', 'W' };
	List<String> equipment;
	// Rover has it's own logic class
	public static DStarLite dsl;
	private boolean initializedDSL = false;

	public ROVER_03() {
		System.out.println("ROVER_03 rover object constructed");
		rovername = "ROVER_03";
		SERVER_ADDRESS = "localhost";
		// in milliseconds - smaller is faster, but the server will cut connection if too small
		sleepTime = 300; 
	}

	public ROVER_03(String serverAddress) {
		// constructor
		System.out.println("ROVER_03 rover object constructed");
		rovername = "ROVER_03";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server
							// will cut connection if it is too small
	}

	/**
	 * Connects to the swarm server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException {
	
			// Make connection to SwarmServer and initialize streams
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);

			// Process all messages from server, wait until server requests
			// Rover ID
			// name - Return Rover Name to complete connection
			while (true) {
				String line = in.readLine();
				if (line.startsWith("SUBMITNAME")) {
					out.println(rovername); // This sets the name of this
											// instance
											// of a swarmBot for identifying the
											// thread to the server
					break;
				}
			}

			// ********* Rover logic setup *********

			String line = "";
			Coord rovergroupStartPosition = null;
			Coord targetLocation = null;

			/**
			 * Get initial values that won't change
			 */
			// **** get equipment listing ****
			equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");

			// **** Request START_LOC Location from SwarmServer ****
			out.println("START_LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println(rovername + " check connection to server");
				line = "";
			}
			if (line.startsWith("START_LOC")) {
				rovergroupStartPosition = extractLocationFromString(line);
			}
			System.out.println(rovername + " START_LOC " + rovergroupStartPosition);

			// **** Request TARGET_LOC Location from SwarmServer ****
			out.println("TARGET_LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println(rovername + " check connection to server");
				line = "";
			}
			if (line.startsWith("TARGET_LOC")) {
				targetLocation = extractLocationFromString(line);
			}
			System.out.println(rovername + " TARGET_LOC " + targetLocation);

			/*****************************************************
			 * MOVEMENT METHODS ASTAR OR DSTAR -- COMMENT OUT ONE
			 * ***************************************************/
			//moveDStar(line, rovergroupStartPosition, targetLocation);
			moveAStar(line, rovergroupStartPosition, targetLocation);

			// This catch block closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					System.out.println("ROVER_03 problem closing socket");
				}
			}
		}

	}
	
	/*****************************
	 * A_STAR STUFF
	 ****************************/
	
	public void moveAStar(String line, Coord startLoc, Coord targetLoc) throws IOException, InterruptedException{
		
		Astar astar = new Astar(1000, 1000, startLoc, targetLoc);
		
		Coord currentLoc = null;
		boolean destReached = false;
		char dir = ' ';
		int counter = 1;
		
		Random rand = new Random();
		
		while (true) {
			
			// **** location call ****
			out.println("LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println("ROVER_03 check connection to server");
				line = "";
			}
			if (line.startsWith("LOC")) {
				currentLoc = extractLocationFromString(line);
			}
			
			if (currentLoc.equals(targetLoc)) {
				destReached = true;
			}
			
			System.out.println("Current Loc: " + currentLoc.toString());

			// **** get equipment listing ****
			ArrayList<String> equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println("ROVER_03 equipment list results " + equipment + "\n");

			this.doScan();
			astar.addScanMap(scanMap, currentLoc, RoverToolType.getEnum(equipment.get(1)), RoverToolType.getEnum(equipment.get(2))); // this																										
			astar.debugPrintRevealCounts(currentLoc, RoverToolType.getEnum(equipment.get(1)), RoverToolType.getEnum(equipment.get(2)));
			scanMap.debugPrintMap();
			
			//codition if reached
			//implement findpath
			
			if (!destReached) {
				dir = astar.findPath(currentLoc, targetLoc, RoverDriveType.WHEELS);
			} else {
				if (counter % 20 == 0) {
					List<String> dirsCons = new ArrayList<>();
					char dirOpposite = getOpposite(dir);
					for (int i = 0; i < cardinals.length; i++) {
						if (cardinals[i] != dirOpposite) {
							dirsCons.add(String.valueOf(cardinals[i]));
						}
					}
					dir = dirsCons.get(rand.nextInt(3)).charAt(0);					
				}
				counter++;
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				int centerIndex = (scanMap.getEdgeSize() - 1) / 2;
				System.out.println(dir);
				switch (dir) {
				case 'N':
					if (northBlocked(scanMapTiles, centerIndex)) {
						dir = resolveNorth(scanMapTiles, centerIndex);
					}
					break;
				case 'S':
					if (southBlocked(scanMapTiles, centerIndex)) {
						dir = resolveSouth(scanMapTiles, centerIndex);
					}
					break;
				case 'E':
					System.out.println("E");
					if (eastBlocked(scanMapTiles, centerIndex)) {
						dir = resolveEast(scanMapTiles, centerIndex);
					}
					break;
				case 'W':
					System.out.println("W");
					if (westBlocked(scanMapTiles, centerIndex)) {
						dir = resolveWest(scanMapTiles, centerIndex);
					}
					break;
				}
				System.out.println("Going: " + dir);
			}
			if (dir != 'U') {
				out.println("MOVE " + dir);
			}
				
			Thread.sleep(sleepTime);
			System.out.println("ROVER_03 ------------ bottom process control --------------");
		}
		
	}
	
	public char getOpposite(char dir) {
		char opposite = ' ';
		switch (dir) {
		case 'N':
			opposite = 'S';
			break;
		case 'S':
			opposite = 'N';
			break;
		case 'E':
			opposite = 'W';
			break;
		case 'W':
			opposite = 'E';
			break;
		}
		System.out.println("Opposite of " + dir + " is " + opposite);
		return opposite;
	}
	//for north
	public char resolveNorth(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "N";
		if (!eastBlocked(scanMapTiles, centerIndex))
			currentDir = "E";
		else if (!westBlocked(scanMapTiles, centerIndex))
			currentDir = "W";
		else
			currentDir = "S";
		return currentDir.charAt(0);
	}
	
	//for south
	public char resolveSouth(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "S";
		if (!westBlocked(scanMapTiles, centerIndex))
			currentDir = "W";
		else if (!eastBlocked(scanMapTiles, centerIndex))
			currentDir = "E";
		else {
			currentDir = "N";
		}
		return currentDir.charAt(0);
	}

	//east
	public char resolveEast(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "E";
		if (!southBlocked(scanMapTiles, centerIndex))
			currentDir = "S";
		else if (!northBlocked(scanMapTiles, centerIndex))
			currentDir = "N";
		else
			currentDir = "W";
		return currentDir.charAt(0);
	}

	//west
	public char resolveWest(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "W";
		if (!northBlocked(scanMapTiles, centerIndex))
			currentDir = "N";
		else if (!southBlocked(scanMapTiles, centerIndex))
			currentDir = "S";
		else
			currentDir = "E";
		return currentDir.charAt(0);
	}
	
	
	//for northblocked
	public boolean northBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex][centerIndex - 1].getHasRover()
				|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.SAND);
	}

	
	//for southblocked
	public boolean southBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex][centerIndex + 1].getHasRover()
				|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.SAND);
	}

	//for eastblocked
	public boolean eastBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex + 1][centerIndex].getHasRover()
				|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.SAND);
	}

	//for westblocked
	public boolean westBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex - 1][centerIndex].getHasRover()
				|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.SAND);
	}

 // END of Rover main control loop

	// ####################### Support Methods #############################

	public int getRandom(int length) {
		Random random = new Random();
		return random.nextInt(length);
	}

	/******************************************
	 * D_STAR STUFF
	 ****************************************/
	public void moveDStar(String line, Coord start, Coord target) throws Exception {
		Coord curTarget = target;
		dsl = new DStarLite(RoverDriveType.getEnum(equipment.get(0)));
		;
		// boolean goingForward = true;
		boolean stuck = false; // just means it did not change locations between
								// requests,
		boolean blocked = false;// could be velocity limit or obstruction etc.

		// int currentDirection = getRandom(cardinals.length);
		Coord currentLoc = null;
		Coord previousLoc = null;
		// int stepCount = 0;
		int stuckCount = 0;

		/**
		 * #### Rover controller process loop ####
		 */
		while (true) {
			// **** Request Rover Location from SwarmServer ****
			out.println("LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println(rovername + " check connection to server");
				line = "";
			}
			if (line.startsWith("LOC")) {
				// loc = line.substring(4);
				currentLoc = extractLocationFromString(line);

			}
			if (initializedDSL)
				dsl.updateStart(currentLoc);
			if (!initializedDSL) {
				dsl.goal_c = target;
				dsl.start_c = currentLoc;
				dsl.init(currentLoc, target);
				dsl.replan();
				initializedDSL = true;
			}

			System.out.println(rovername + " currentLoc at start: " + currentLoc);

			previousLoc = currentLoc;
			// if we've reached our destination, get a new one
			// for now go to (4, 4) on the map
			if (currentLoc.equals(curTarget)) {
				curTarget = new Coord(4, 4);
				dsl.updateGoal(curTarget);
			}
			// ***** do a SCAN *****
			doScan();
			// prints the scanMap to the Console output for debug purposes
			scanMap.debugPrintMap();

			// ***** get TIMER remaining *****
			checkTime(line);

			// ***** MOVING *****
			MapTile[][] scanMapTiles = scanMap.getScanMap();
			// update/add new mapTiles to dsl hashMaps
			updateScannedStates(scanMapTiles, currentLoc);
			// find path from current node to goal
			dsl.replan();
			char move = getMoveFromPath(currentLoc);
			// try to move
			System.out.println("Requesting to move " + move);
			out.println("MOVE " + move);
			Thread.sleep(300);

			// another call for current location
			out.println("LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println("ROVER_03 check connection to server");
				line = "";
			}
			if (line.startsWith("LOC")) {
				currentLoc = extractLocationFromString(line);
			}

			// test for stuckness - if stuck for too long try switching
			// positions
			stuck = currentLoc.equals(previousLoc);
			if (stuck)
				stuckCount += 1;
			else
				stuckCount = 0;
			if (stuckCount >= 10)
				out.println("MOVE " + move);

			System.out.println("ROVER_03 blocked test " + blocked);
			// this is the Rovers HeartBeat, it regulates how fast the Rover
			// cycles through the control loop
			Thread.sleep(sleepTime);

			System.out.println("ROVER_03 ------------ bottom process control --------------");
		}
	}
	
	public void checkTime(String line) throws IOException {
		out.println("TIMER");
		line = in.readLine();
		if (line == null) {
			System.out.println(rovername + " check connection to server");
			line = "";
		}
		if (line.startsWith("TIMER")) {
			String timeRemaining = line.substring(6);
			System.out.println(rovername + " timeRemaining: " + timeRemaining);
		}
	}

	/*
	 * This method feeds maptils from scan to DStarLite object for updating
	 * states/nodes have to find coordinates for each tile, given that the
	 * center is current location
	 */
	public void updateScannedStates(MapTile[][] tiles, Coord current) {
		int centerRow = (tiles.length - 1) / 2;
		int centerCol = (tiles[0].length - 1) / 2;
		// System.out.println("rows: " + tiles.length + " cols: " +
		// tiles[0].length + " centers: " + centerRow);
		for (int row = 0; row < tiles.length; row++) {
			for (int col = 0; col < tiles[0].length; col++) {
				int xPos = findCoordinate(col, current.xpos, centerCol);
				int yPos = findCoordinate(row, current.ypos, centerRow);
				Coord newCoord = new Coord(xPos, yPos);
				// updateCell also adds new cells if they're not already in
				// tables
				if (newCoord.equals(current))
					continue;
				dsl.updateCell(newCoord, tiles[col][row]);
			}
		}
		System.out.println("Updated neighbors to center");
		dsl.scanElem += 4;
	}

	/*
	 * Given the scan map, it finds the coordinates of each tile relative to
	 * rover's position - for updating States.
	 */
	public int findCoordinate(int n, int pivot, int centerIndex) {
		int pos;
		int diff = Math.abs(n - centerIndex);
		if (n > centerIndex)
			pos = pivot + diff;
		else if (n < centerIndex)
			pos = pivot - diff;
		else
			pos = pivot;
		return pos;
	}

	/*
	 * Gets 2nd element from the path, first is current position. returns the
	 * cardinal index to move to that position.
	 */
	public char getMoveFromPath(Coord current) {
		int index = 0;
		State nextState;
		while (true) {
			if (current.equals(dsl.getPath().get(index).getCoord())) {
				index++;
				nextState = dsl.getPath().get(index);
				System.out.println("Using index: " + index);
				break;
			} else {
				index++;
			}
		}
		int newX = nextState.getCoord().xpos;
		int newY = nextState.getCoord().ypos;
		if (newX > current.xpos)
			return cardinals[1];
		else if (newX < current.xpos)
			return cardinals[3];
		else if (newY > current.ypos)
			return cardinals[2];
		else
			return cardinals[0];
	}
	/**********
	 * End D_Star
	 ********/
	
	// ####################### Support Methods #############################
	private void clearReadLineBuffer() throws IOException{
		while(in.ready()){
			//System.out.println("ROVER_03 clearing readLine()");
			in.readLine();	
		}
	}

	// method to retrieve a list of the rover's EQUIPMENT from the server
	private ArrayList<String> getEquipment() throws IOException {
		// System.out.println("ROVER_03 method getEquipment()");
		Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
		out.println("EQUIPMENT");

		String jsonEqListIn = in.readLine(); // grabs the string that was
												// returned first
		if (jsonEqListIn == null) {
			jsonEqListIn = "";
		}
		StringBuilder jsonEqList = new StringBuilder();
		// System.out.println("ROVER_03 incomming EQUIPMENT result - first
		// readline: " + jsonEqListIn);

		if (jsonEqListIn.startsWith("EQUIPMENT")) {
			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
				// if (jsonEqListIn == null) {
				// break;
				// }
				// System.out.println("ROVER_03 incomming EQUIPMENT result: " +
				// jsonEqListIn);
				jsonEqList.append(jsonEqListIn);
				jsonEqList.append("\n");
				// System.out.println("ROVER_03 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return null; // server response did not start with "EQUIPMENT"
		}

		String jsonEqListString = jsonEqList.toString();
		ArrayList<String> returnList;
		returnList = gson.fromJson(jsonEqListString, new TypeToken<ArrayList<String>>() {
		}.getType());
		// System.out.println("ROVER_03 returnList " + returnList);

		return returnList;
	}

	// sends a SCAN request to the server and puts the result in the scanMap
	// array
	public void doScan() throws IOException {
		// System.out.println("ROVER_03 method doScan()");
		Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); // grabs the string that was
												// returned first
		if (jsonScanMapIn == null) {
			System.out.println("ROVER_03 check connection to server");
			jsonScanMapIn = "";
		}
		StringBuilder jsonScanMap = new StringBuilder();
		System.out.println("ROVER_03 incomming SCAN result - first readline: " + jsonScanMapIn);

		if (jsonScanMapIn.startsWith("SCAN")) {
			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
				// System.out.println("ROVER_03 incomming SCAN result: " +
				// jsonScanMapIn);
				jsonScanMap.append(jsonScanMapIn);
				jsonScanMap.append("\n");
				// System.out.println("ROVER_03 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return; // server response did not start with "SCAN"
		}
		// System.out.println("ROVER_03 finished scan while");

		String jsonScanMapString = jsonScanMap.toString();
		// debug print json object to a file
		// new MyWriter( jsonScanMapString, 0); //gives a strange result -
		// prints the \n instead of newline character in the file

		// System.out.println("ROVER_03 convert from json back to ScanMap
		// class");
		// convert from the json string back to a ScanMap object
		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);
	}

	// this takes the server response string, parses out the x and x values and
	// returns a Coord object
	public static Coord extractLocationFromString(String sStr) {
		int indexOf;
		indexOf = sStr.indexOf(" ");
		sStr = sStr.substring(indexOf + 1);
		if (sStr.lastIndexOf(" ") != -1) {
			String xStr = sStr.substring(0, sStr.lastIndexOf(" "));
			// System.out.println("extracted xStr " + xStr);

			String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);
			// System.out.println("extracted yStr " + yStr);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}


	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {

		ROVER_03 client;
		// if a command line argument is included it is used as the map filename
		// if present uses an IP address instead of localhost

		if (!(args.length == 0)) {
			client = new ROVER_03(args[0]);
		} else {
			client = new ROVER_03();
		}

		client.run();
	}
}