import java.io.Serializable;


public class RPCData implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	String callID;
	int op;
	SessionData sessData;
	session sess;
	View view;
	

	public String toString(){
		String x = callID+"#"+op+"#";
		return x;
	}
	public RPCData(String _callID, int _op, session _sess){
		callID = new String(_callID);
		sess=_sess;
		view=null;
		sessData=null;
	}
	public RPCData(String _callID,int _op, SessionData _sessData){
		callID = new String(_callID);
		op = _op;
		sessData=_sessData;
		view=null;
		sess=null;
	}
	public RPCData(String _callID,int _op, View _view){
		callID = new String(_callID);
		op = _op;
		view = _view;
		sessData=null;
		sess=null;
	}
	public RPCData(String _callID,int _op){
		callID = new String(_callID);
		op = _op;

	}
}
