package novajoy.janitor;

import novajoy.util.config.IniWorker;
import novajoy.util.db.JdbcManager;

public class StartJanitor {
	public static void main(String[] args) throws Exception {
		IniWorker config = new IniWorker(
				"/Users/prikhv/IdeaProjects/novajoy/config/config.ini"
		);
		JdbcManager dbman = new JdbcManager(config.getDBaddress(),
				config.getDBbasename(), config.getDBuser(),
				config.getDBpassword());
		Janitor jan = new Janitor(dbman);
		jan.clean_rssitems();
		jan.clean_spoiled();
	}
}
