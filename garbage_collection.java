import java.util.concurrent.ConcurrentHashMap;





public class garbage_collection extends Thread{
	
	//check and delete expired session in hashtable every 1 minute
	private static final int wait_time = 60000; 
	private ConcurrentHashMap<String, session> table;
	
	public garbage_collection(ConcurrentHashMap<String, session> table){
		this.table=table;
	
	}
	
	public void run() {
		while (true) {		
			try {
				System.out.println("Waiting zzzzzzz");
				Thread.sleep(wait_time);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			System.out.println("Checking Garbage =____=");
			
			for (ConcurrentHashMap.Entry<String, session>  elm: table.entrySet()) {
				if (elm.getValue().Expired()) {
					table.remove(elm.getKey());
					System.out.println("Expired Cookie removed: " + elm.getValue().toString());
				}
			}
	   }
		
		
	}
	
}
