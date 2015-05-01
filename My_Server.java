import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;




/**
 * Servlet implementation class My_Server
 */
@WebServlet("/My_Server")
public class My_Server extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static ConcurrentHashMap<String,session> table = new ConcurrentHashMap<String, session>(); 
	
	private static final String cookie_name	= "CS5300PROJ1SESSION";
	private static final String DEFAULT_MESSAGE	= "Hello World";
    private static int environment = 0;  //0 for local, 1 for EC2
    
	private final String localSvrID;
	
	// we set k equals to 3 in this case
	public static final int data_copies_num = 4;

	public static RPC rpc;
	public static View view;

	private static final int GOSSIP_SECS = 10000;
	public static String dataSourceIP;
	
	public My_Server() throws InterruptedException  {
        super();
        
        //start a new thread that runs hashtable garbage collection
        garbage_collection GC= new garbage_collection(table);
        GC.start();
        
        //new instance of RPC
        rpc = new RPC();
        rpc.start();
        
        //get IP
        localSvrID = get_IP();
        
        //create view table 
        view = new View();
        view.viewtable.put(localSvrID, new ViewItem(System.currentTimeMillis(), "up"));

        view.gossipDB();
        
        //create gossip thread
        Gossip gossip = new Gossip();
        gossip.start();
    }
	// Gossip thread 
	// keep gossiping with other servers or simpleDB
	private static class Gossip extends Thread{
    	public void run() {
    		try {
    			while(true){
    				System.out.println("gossip thread");
    				view.viewtable.put(get_IP(), new ViewItem(System.currentTimeMillis(), "up"));
    				Random r = new Random();
    				Thread.sleep( GOSSIP_SECS/2 + r.nextInt( GOSSIP_SECS+1));
    				
    				//garbage collect on view table
    				view.garbageCollect();
    				System.out.println("after collection:"+view.viewtable);
	    			
    				//random choose one view item or simpleDB to gossip
    				ArrayList<String> keysAsArray = new ArrayList<String>(view.viewtable.keySet());				
    				int index = r.nextInt(keysAsArray.size()+1);
    				if(index == keysAsArray.size()){
    					System.out.println("before gossip with DB");
    					view.gossipDB();
    					System.out.println("done gossip with DB");
    				}else{
    					System.out.println("before gossip with server");
    					String serverID = keysAsArray.get(index); 
    					System.out.println("Gossip target IP:"+serverID);
    					view.gossipServer(serverID);
    					System.out.println("done gossip with server");
    				}	    					
				}
    		} catch (InterruptedException e) {
				// Auto-generated catch block
				e.printStackTrace();
    		} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
	}
	// get_IP using different function by the arg of environment.
	public static String get_IP(){
		  if(environment==0){
	        	return get_localIP();
	        }else{
	        	return get_AWSIP();
	        }
	}
    // get local_ip
	public static String get_localIP(){
    	String ip = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // filters out 127.0.0.1 and inactive interfaces
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    ip = addr.getHostAddress();
                   
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return ip;
    }
	
	//get aws_ip
	public static String get_AWSIP(){
		try {
			Process p = Runtime.getRuntime().exec("/opt/aws/bin/ec2-metadata --public-ipv4");
			BufferedReader in = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
			String line = null;
            while ((line = in.readLine()) != null) {
                System.out.println("AWS IP Address: "+line);
            }
            in.close();
            return line;
		}catch (IOException e) {
            e.printStackTrace();
        }
		return null;
		
		  
	}

    
    //create updated version of the web page according to the sessionData
	protected void printPage(HttpServletResponse response,SessionData sessData,String value,boolean flag) throws Exception{
    	PrintWriter writer=response.getWriter();
    	response.setContentType("text/html");
    	writer.write("<html><head><style> table, th, td { border: 1px solid black;border-collapse: collapse;}th, td {padding: 5px;text-align: left;}</style></head>");
    	String message = "";
    	if (sessData!=null) message= sessData.message;
    	writer.print("<body><h1>Message: " + message + "</h1>"+
    			"<h1>Processing Server: " + get_IP() + "</h1>"+
    			"<h1>Data Server: " + dataSourceIP + "</h1>"+
    			"<FORM ACTION='My_Server' method='get' name='form'>"+
    			"<input type='text' name='message' value=''>"+
    			"<input type='submit' name='Replace' value='Replace'>"+
    			"<input type='submit' name='Refresh' value='Refresh'>"+
    			"<input type='submit' name='Logout' value='Logout'>"+
    			"</Form></br>"+
    			view.toString()
    			);
    	String curSessID="";
    	if(sessData!=null){
    		curSessID = sessData.sessID;
    		writer.println("<p>SessionID: "+curSessID+"</p>");
    		writer.println("<p>Version: "+sessData.versionNum+"</p>");
		
	    	if(sessData.SvrIDs.size() == 0)
				writer.println("<p>Data Server(s): None</p>");
			else
				writer.println("<p>Data Server(s): "+sessData.SvrIDs+"</p>");
		
			if(flag){
				writer.println("Session expiration time(minutes): " + Integer.toString(session.get_expiration_time()/60000) + "\n");
			}else{
				writer.println("<p>Logout ,Current No Session<p/>");
			}
			writer.println("<p>Session discard time(milliseconds): "+(System.currentTimeMillis()+session.expirationLong)+"</p></body></html>");
    	}else{
    		writer.println("<h1>Message: LOGOUT</h1>");
    	}
   }
    // check if cookie is valid
    private boolean ValidCookie(Cookie c){
    	String val = c.getValue();
    	if (val == null )return false;
    	String[] val_list=val.split("#");
    	if(val_list.length<3) return false;
    	if (Integer.parseInt(val_list[1])<0)return false;
    	return true;
    	
    }
    //create or find cookie and update information, handle refresh and replace operation
	protected void checkCookie(HttpServletRequest request, HttpServletResponse response) throws Exception{
		dataSourceIP = get_IP();
		// check of coockies
		Cookie curCookie = null;
		String curCookieVal;
		String curSessionID = null;
		int curVersion = 0;
		
		ArrayList<String> svrList = null;
		Cookie[] cookie_list=request.getCookies();
		if(cookie_list!=null) {
			System.out.println("lookup cookie");
			for (Cookie c: cookie_list){
			    if(c.getName().equals(cookie_name) && ValidCookie(c)){
			    	curCookie = c;
			    	break;
			    }		
			}
		}
		if(curCookie!=null){
			// get cookie information about servers
			svrList = new ArrayList<String>();
 			curCookieVal = curCookie.getValue();
			String[] val_list=curCookieVal.split("#");
			curSessionID = val_list[0];
			curVersion = Integer.parseInt(val_list[1]);
			for(int i=2;i<val_list.length;i++){
				svrList.add(val_list[i]);
			}
		}

		if(request.getParameter("Logout")!=null){
			//logout
			    System.out.println("Logout!!!!!!!!!!!");
			    SessionData curSessData=null;
			    if(curCookie!=null){
			    	curSessData = new SessionData(curSessionID,-1,"Logout Cookie",svrList);
			    	rpc.SessionWriteClient(curSessData,0); 
			    	curCookie.setMaxAge(0);
		    		response.addCookie(curCookie);
			    }
			    curSessData = null;
			    printPage(response,curSessData,"",false);
		} else {
		//replace & refresh 
				System.out.println("refresh or replace !!!!!!!!!!!");
				SessionData curSessData = null;
				
				if(curCookie!=null){
				 //valid cookie 
					if(table.containsKey(curSessionID)){
					 //check local	
					 	session curSession = table.get(curSessionID);
					 	if(!curSession.Expired()&&curSession.version>=curVersion){
					 			System.out.println("Local session");
					 			curSessData = new SessionData(curSession,svrList);
					 			
			    		 }
				 
					}
					if(curSessData == null){
						 //no local session or invalid session local
						 session tmpSess = rpc.SessionReadClient(new SessionData(curSessionID,curVersion,svrList));
						 if(tmpSess != null)
							 curSessData = new SessionData(tmpSess,svrList);
					}
				}
				if(curCookie == null||curSessData == null){
					//noSession Exist Create
					System.out.println("No session exists");
					String newSessionID = UUID.randomUUID()+"_"+localSvrID;
					svrList = view.availableNodes(data_copies_num, null);
					curSessData= new SessionData(newSessionID,0,DEFAULT_MESSAGE,svrList);
				}
			
				if(request.getParameter("Replace")!=null){
					curSessData.message = request.getParameter("message");
				}
				//after all operation create cookie and sessions with the sessionData get from above
				ArrayList<String> retSvrList = rpc.SessionWriteClient(curSessData,1); 
				String value=curSessData.sessID + "#" + curSessData.versionNum;
				for(String s:retSvrList)
					value=value+"#"+s;
				curSessData.SvrIDs = retSvrList;
			
				Cookie new_cookie = new Cookie(cookie_name, value);
				new_cookie.setMaxAge(session.get_expiration_time()/1000);
				response.addCookie(new_cookie);
				printPage(response,curSessData,value,true);
			}
    }
    // retrieve session from Sessiontable
    public static session retrieve(SessionData sessData){
    	session tmpSession = table.get(sessData.sessID);
    	if (tmpSession!=null && sessData.versionNum<=tmpSession.version)
    	return tmpSession;
    	else return null;
    }
    //write session to sessiontable
    public static boolean write(SessionData sessData){ 
    	if(sessData!=null){
    		System.out.println("write succ");
    		session instance=new session(sessData.message,sessData.sessID,sessData.versionNum+1);
    		System.out.println(table.put(sessData.sessID, instance)!=null);
    		return table.put(sessData.sessID, instance)!=null;
    	}else{
    		System.out.println("write fail");
    		return false;
    	}
    }
    
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		
		try {
			checkCookie(request,response);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		try {
			checkCookie(request,response);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
