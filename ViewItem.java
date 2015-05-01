import java.io.Serializable;


public class ViewItem implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public long time;
	public String status;
	
	public ViewItem(long t, String s){
		time = t;
		status = s;
	}
}
