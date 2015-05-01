import java.io.Serializable;
import java.util.ArrayList;


public class SessionData implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	String sessID;
	int versionNum;
	String message;

	ArrayList<String> SvrIDs;
	
	public SessionData(String sessID,int versionNum,String message, ArrayList<String> SvrIDs){
		this.sessID=sessID;
		this.SvrIDs=SvrIDs;
		this.versionNum = versionNum;
		this.message = message;
	}
	public SessionData(session sess,ArrayList<String> SvrIDs){
		this.versionNum = sess.version;
		this.message = sess.message;
		this.sessID=sess.SessID;
		this.SvrIDs=SvrIDs;
		
	}
	public SessionData(String sessID,int versionNum, ArrayList<String> SvrIDs){
		this.sessID=sessID;
		this.SvrIDs=SvrIDs;
		this.versionNum = versionNum;
	}
}
