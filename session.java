import java.io.Serializable;



public class session implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String message;
	public String SessID;
	public int version;
	public long discard_time;
	
	
	
	//The expiration time for a session (5 minute)
	private static final int expiration = 300000;
	public static final long expirationLong = 300000;
	private static final int delta = 1000;
	public session(SessionData sessData){
		message=sessData.message;
		SessID = sessData.sessID;
		version = sessData.versionNum;
		this.discard_time=System.currentTimeMillis()+expiration+delta;
	}
	public session(String message,String session_id,int version){
		this.message=message;
		this.SessID=session_id;
		this.version=version;
		this.discard_time=System.currentTimeMillis()+expiration+delta;
	}
	
	
	public void updateTime(){
		this.discard_time=System.currentTimeMillis()+expiration+delta;
	}
	
	
	public boolean Expired() {
		
		return discard_time<System.currentTimeMillis();
	}
	
	public static int get_expiration_time(){
		return expiration;
	}
	
	public String toString(){
		String x = message;
		return x;
	}
	
}