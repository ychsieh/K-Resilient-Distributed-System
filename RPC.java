import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.UUID;

 class RPC extends Thread{
	 public static final int portProj1bRPC = 5001;
	 public static final int maxPacketSize = 30000;
	 public static final int operationSESSIONREAD = 1;
	 public static final int operationSESSIONWRITE = 2;
	 public static final int operationSESSIONEXCHANGE = 3;
	 public static final int rpcTimeOut = 1000;
	 
	 //Exchange View
	 //exchange view with specific server and using view and the server id as input 
	 //return the merged view from the server
	 
	 public View exchangeViews (View view,String SvrID) throws IOException, ClassNotFoundException{
		 	System.out.println("Merge Client");
			DatagramSocket rpcSocket = new DatagramSocket();
			rpcSocket.setSoTimeout(rpcTimeOut);
			String callID = UUID.randomUUID().toString();
			RPCData recvRpc=null;

			//add view to the RPCData and translate to byte array
			
			RPCData tempOut =new RPCData(callID,operationSESSIONEXCHANGE,My_Server.view);
			byte[] outBuf= Serializer.serialize(tempOut);
			System.out.println(outBuf.length);
			
			//get server address by the serverip string
			InetAddress addr = (InetAddress.getByName(SvrID));
			
			//send out pkt to the specific server
			DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, addr, portProj1bRPC);
			
			rpcSocket.send(sendPkt);
			System.out.println("after send to server");
			byte [] inBuf = new byte[maxPacketSize];
			// receive response from server
			DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			try {
				do {
			      recvPkt.setLength(inBuf.length);
			      rpcSocket.receive(recvPkt);
			      recvRpc=(RPCData)Serializer.deserialize(recvPkt.getData());
			    } while(!callID.equals(recvRpc.callID));
			} catch(SocketTimeoutException stoe) {
			    // timeout 
				recvPkt = null;
				System.out.println("Time Out");
			}
			catch(IOException ioe) {
			    // other error 
			   System.out.println("IOException");
			}
			rpcSocket.close();
			System.out.println("out of while loop");
			if(recvPkt!=null){
				return recvRpc.view;
			}else return null;	
	 };
	 
	 // Write session data via RPC 
	 // sessData contains the session data and the write server list 
	 // reqNewServer denote if need to request new server  when timeout
	 // we dont need to request new server when write logout to the sessions
	 public ArrayList<String> SessionWriteClient(SessionData sessData, int reqNewServer) throws IOException, ClassNotFoundException{
		DatagramSocket rpcSocket = new DatagramSocket();
		rpcSocket.setSoTimeout(rpcTimeOut);
		String callID = UUID.randomUUID().toString();
		ArrayList<String> retList=new ArrayList<String>();
		RPCData recvRpc=null;
			//add readArgs SessionID
		System.out.println("write Client");
		int kServer = My_Server.data_copies_num;
		System.out.println("ori list num:"+sessData.SvrIDs.size()+"   kserver:"+kServer);
		
		if(sessData.SvrIDs.size()<kServer){
			ArrayList<String> newSvrs;
			newSvrs = My_Server.view.availableNodes(kServer-sessData.SvrIDs.size(),sessData.SvrIDs);
			System.out.println("get new server num:"+newSvrs.size());
			sessData.SvrIDs.addAll(newSvrs);
		}
		
		kServer = sessData.SvrIDs.size();
		ArrayList<String> newSvrs = sessData.SvrIDs;
		
		
		RPCData tempOut =new RPCData(callID,operationSESSIONWRITE,sessData);
		byte[] outBuf= Serializer.serialize(tempOut);
		
		System.out.println("Write Svr Num:"+sessData.SvrIDs.size()+",session ID:"+sessData.sessID);
	
		
		byte [] inBuf = new byte[maxPacketSize];
		
		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		// send out the packets and wait for response 
		while(kServer!=0){
			//send out packets 
			for(  String backup:newSvrs ) {
				DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, InetAddress.getByName(backup), portProj1bRPC);
			    rpcSocket.send(sendPkt);
			    System.out.println(sendPkt.getAddress().getHostAddress());
			}
			//try to get response
			try {
				System.out.println(" send "+kServer+" servers");
				do {
			      recvPkt.setLength(inBuf.length);
			      rpcSocket.receive(recvPkt);
			      recvRpc=(RPCData)Serializer.deserialize(recvPkt.getData());
			      
			      System.out.println("recv:"+recvRpc.callID+",callID:"+callID+","+recvRpc.op);
			      if(recvRpc.op == 1&& callID.equals(recvRpc.callID)){
			    	  kServer--;
			    	  System.out.println("recvsucc");
			    	  retList.add(recvPkt.getAddress().getHostAddress());
			    	  My_Server.view.updateView(recvPkt.getAddress().getHostAddress(),"up");
			      }
			      System.out.println("write while loop");
			    } while(kServer>0);
			} catch(SocketTimeoutException stoe) {
				
			    // process timeout  
				// mark the down server
				// check if need to request new server 
				for (String svr:newSvrs){
					int match=0;
					for(String x:retList){
						if (svr.equals(x)){
							match=1;
						}
					}
					if(match == 0){
						//mark server down
						My_Server.view.updateView(svr,"down");
					}
				}
				if(reqNewServer==1){
					newSvrs = My_Server.view.availableNodes(kServer,retList);
					kServer = newSvrs.size();
					
				}else 
					kServer=0;
			}
			catch(IOException ioe) {
			    // other error 
			   System.out.println("IOException");
			}
		}
		rpcSocket.close();
		
		
		System.out.println("retList:"+retList);
		return retList;
	 }
	 	//
		//   SessionReadClient(sessionID)
		//   sending to multiple destAddrs, all at port = portProj1bRPC
	 	//   using a single pre-existing DatagramSocket object rpcSocket
		//
	 public session SessionReadClient(SessionData sessData) throws IOException, ClassNotFoundException{
			
		 	System.out.println("read Client");
			DatagramSocket rpcSocket = new DatagramSocket();
			rpcSocket.setSoTimeout(rpcTimeOut);
			String callID = UUID.randomUUID().toString();
			RPCData recvRpc=null;

			//add readArgs SessionID
			RPCData tempOut =new RPCData(callID,operationSESSIONREAD,sessData);
			
			byte[] outBuf= Serializer.serialize(tempOut);
			//add primary SvrID and backupSvrID

			//send out pkt to all Svr: primary and backups
			System.out.println("Read Svr Num:"+sessData.SvrIDs.size()+",session ID:"+sessData.sessID);
			for(  String backup:sessData.SvrIDs ) {
				DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, InetAddress.getByName(backup), portProj1bRPC);
			    rpcSocket.send(sendPkt);
			}
			
			//recv reponse 
			byte [] inBuf = new byte[maxPacketSize];
			DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			try {
				while(true){
			      recvPkt.setLength(inBuf.length);
			      rpcSocket.receive(recvPkt);
			      recvRpc=(RPCData)Serializer.deserialize(recvPkt.getData());
			      if(recvRpc.sess!=null && callID.equals(recvRpc.callID)&&recvRpc.sess.version>=sessData.versionNum){
			    	  My_Server.dataSourceIP = recvPkt.getAddress().getHostAddress();
			    	  break;
			      }  
			    } 
			} catch(SocketTimeoutException stoe) {
			    // timeout 
				recvPkt = null;
				System.out.println("Time Out");
			}
			catch(IOException ioe) {
			    // other error 
			   System.out.println("IOException");
			}
			rpcSocket.close();
			System.out.println("read end");
			if(recvPkt!=null){
				String SvrID = recvPkt.getAddress().getHostAddress();
				System.out.println("read get response from :"+SvrID);
				My_Server.view.updateView(SvrID,"up");
				return recvRpc.sess;
			}
			else 
				return null;

			
	 }
	 
	 
	 
	// Continually running server for listening the socket for operation
	 
	 @SuppressWarnings("resource")
	public void SessionServer() throws IOException, ClassNotFoundException{
		 DatagramSocket rpcSocket = new DatagramSocket(portProj1bRPC);
		 RPCData newRpc = null;
		 System.out.println("Server started");
		 while(true) {
		    byte[] inBuf = new byte[maxPacketSize];
		    DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		
			rpcSocket.receive(recvPkt);
			
		    InetAddress returnAddr = recvPkt.getAddress();
		    int returnPort = recvPkt.getPort();
		    // here inBuf contains the callID and operationCode
		    System.out.println("pktLen:"+recvPkt.getData().length);
		    RPCData rpcData=(RPCData)Serializer.deserialize(recvPkt.getData());
		    System.out.println( "rpc operation code:"+rpcData.op);
		    byte[] outBuf = null;
		    switch( rpcData.op ) {
		    	// 3 kinds operations
		    	case operationSESSIONREAD:
		    		System.out.println("operationSESSIONREAD");
		    		// SessionRead accepts call args and returns call results 
		    		newRpc = new RPCData(rpcData.callID,0,My_Server.retrieve(rpcData.sessData));
		    		outBuf= Serializer.serialize(newRpc);
		    		break;
		    	case operationSESSIONWRITE:
		    		System.out.println("operationSESSIONWRITE");
		 		   	if (My_Server.write(rpcData.sessData)) newRpc = new RPCData(rpcData.callID,1);
		 		   	else newRpc = new RPCData(rpcData.callID,0);
		 		    outBuf= Serializer.serialize(newRpc);
		    		break;
		    	case operationSESSIONEXCHANGE:
		    		System.out.println("operationSESSIONEXCHANGE");
		    		My_Server.view.mergeView(rpcData.view);
		    		newRpc = new RPCData(rpcData.callID,0,My_Server.view);
		    		outBuf= Serializer.serialize(newRpc);
		    		break;
		    		
		    }
		    // here outBuf should contain the callID and results of the call
		    DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length,
		    	returnAddr, returnPort);
		    System.out.println("send Back : "+newRpc.callID+newRpc.op);
			rpcSocket.send(sendPkt);
		 }
	 }
	
	 public void run(){
		 //RPC Server
		try {
			SessionServer();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		  
	 }
 }