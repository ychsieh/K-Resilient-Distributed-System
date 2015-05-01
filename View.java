import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.*;

public class View implements Serializable{
	private static final long serialVersionUID = 1L;
	private static final long maxPropatime = (long)(Math.log(My_Server.data_copies_num+1)/Math.log(2) * 500 * 2);
	public ConcurrentHashMap<String, ViewItem> viewtable = new ConcurrentHashMap<String, ViewItem>();
	
	//provide a list of "up" servers to assign copies of data to a new server if one of the original server goes down  
	public ArrayList<String> availableNodes(int number, ArrayList<String> now){
		Iterator<String> it = viewtable.keySet().iterator();
		ArrayList<String> result = new ArrayList<String>();
		
		while(it.hasNext() && result.size() < number){
			String key = (String) it.next();
			boolean redundant = false;
			if (now !=null){
				for(String s:now){
					if(key.equals(s))
						redundant = true;
				}
			}
			if(!redundant && viewtable.get(key).status.equals("up"))		
				result.add(key);
		}
		return result;
	}
	
	//garbage collect on view table which is run in background thread every 10 seconds
	public void garbageCollect(){
		Iterator<String> it = viewtable.keySet().iterator();
		while(it.hasNext()){
			String key = (String) it.next();
			//check if a server haven't been touched for longer than 2 minutes
			if(System.currentTimeMillis() > viewtable.get(key).time + 2 * 60 * 1000 && viewtable.get(key).status.equals("up"))
				viewtable.put(key, new ViewItem(System.currentTimeMillis(), "down"));
			
			//check if a server is down for longer than max propagation time
			long deathtime = viewtable.get(key).time + maxPropatime;
			if(System.currentTimeMillis() > deathtime && viewtable.get(key).status.equals("down"))
				viewtable.remove(key);				
		}
	}
	
	// gossip with server
	public void gossipServer(String SvrID) throws InterruptedException, ClassNotFoundException, IOException{	
		View partnerView = My_Server.rpc.exchangeViews(this, SvrID);
		mergeView(partnerView);
		
	}
	
	//merge view with other server
	public void mergeView(View partner){
		if(partner==null)return ;
		Iterator<String> it = partner.viewtable.keySet().iterator();
		while(it.hasNext()){
			String key = (String)it.next();
			ViewItem partneritem = partner.viewtable.get(key);
			if(viewtable.containsKey(key)){
				ViewItem localitem = viewtable.get(key);
				if(localitem.time < partneritem.time)
					viewtable.put(key, partneritem);
			}else if(partneritem.status.equals("up"))
				viewtable.put(key, partneritem);	
		}
	}
	
	//update view
	public void updateView (String SvrID,String status){
		ViewItem item = viewtable.get(SvrID);
		item.status = status;
		item.time = System.currentTimeMillis();		
	}
	
	//gossip with simpleDB
	public void gossipDB() throws InterruptedException {
		
		AmazonSimpleDB sdb = new AmazonSimpleDBClient(basicAWSCredentials);
		Region usEast1 = Region.getRegion(Regions.US_EAST_1);
		sdb.setRegion(usEast1);
		String domain = "ServerView";
		
		try{
			String qry = "select * from `" + domain + "`";
			SelectRequest selectRequest = new SelectRequest(qry);
			List<ReplaceableItem> updatedata = new ArrayList<ReplaceableItem>();
			Set<String> localkeys = new HashSet<String>(viewtable.keySet());
			
			for (Item item : sdb.select(selectRequest).getItems()) {
				String svrID = item.getName();
				List<Attribute> attrs = item.getAttributes();
				long dbtime = 0;
				String dbstat = "";
				for (Attribute at: attrs){
					switch(at.getName()){
						case "time": dbtime = Long.parseLong(at.getValue());
						case "status": dbstat = at.getValue();	
					}
				}			
				
				//check if a server is down for longer than max propagation time
				long deathtime = dbtime + maxPropatime;
				if(System.currentTimeMillis() > deathtime && dbstat.equals("down")){
					System.out.println("delete item!!!");
					sdb.deleteAttributes(new DeleteAttributesRequest(domain, svrID));
					viewtable.remove(svrID);
					localkeys.remove(svrID);
					continue;
				}
				
				//check if a server haven't been touched for 2 minutes
				if(System.currentTimeMillis() > dbtime + 2 * 60 * 1000 && dbstat.equals("up")){
					viewtable.put(svrID, new ViewItem(System.currentTimeMillis(), "down"));
					localkeys.add(svrID);
				}
				
				//compare simpleDB with local view table and merge together
				if(viewtable.containsKey(svrID)){
					ViewItem v = viewtable.get(svrID);
					if(dbtime > v.time)
						viewtable.put(svrID, new ViewItem(dbtime, dbstat));
					else{
						updatedata.add(new ReplaceableItem().withName(svrID).withAttributes(
								new ReplaceableAttribute().withName("status").withValue(v.status).withReplace(true),
								new ReplaceableAttribute().withName("time").withValue(Long.toString(v.time)).withReplace(true)));
					}
					localkeys.remove(svrID);
				}
				else if(dbstat.equals("up"))
					viewtable.put(svrID, new ViewItem(dbtime, dbstat));			
			}
			
			//update local entries to simpleDB if simpleDB doesn't contain those entries
			for(String s: localkeys){
				ViewItem v = viewtable.get(s);
				if(v.status.equals("up")){
					updatedata.add(new ReplaceableItem().withName(s).withAttributes(
							new ReplaceableAttribute().withName("status").withValue(v.status).withReplace(true),
							new ReplaceableAttribute().withName("time").withValue(Long.toString(v.time)).withReplace(true)));
				}
			}
			if(updatedata.size() > 0)
				sdb.batchPutAttributes(new BatchPutAttributesRequest(domain, updatedata));		
		}
		catch (AmazonServiceException ase){
			System.out.println(ase);
		}
		catch (AmazonClientException ace){
			System.out.println(ace);
		}
	}
	
	public String toString(){
		String ret = "<table > <caption> View Table </caption>";
		Iterator<String> it = viewtable.keySet().iterator();
		while(it.hasNext() ){
			ret+= "<tr>";
			String key = (String) it.next();
			ViewItem item=viewtable.get(key);
			ret+= "<td>"+key+"</td>";
			ret+="<td>"+item.status+"</td>"; 
			ret+="<td>"+item.time+"</td>"; 
			ret+="</tr>";
		}
		return ret;
	}
}