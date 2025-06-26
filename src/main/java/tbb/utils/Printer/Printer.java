package tbb.utils.Printer;

public class Printer {
	public static StateHelper sh = new StateHelper();
	
	private static void spinner() {
		
		clearScreen();
	    char[] spinner = {'|', '/', '-', '\\'};
	    int index = 0;
	    try {
	        while (true) {
	        	// move \r back to the beginning if it causes problems outputting errors
	            System.out.print(sh.toString() + "... " + spinner[index] + "               \r"); 
	            System.out.flush();

	            Thread.sleep(300); // can be changed, 300 is arbitrary
	            index = (index + 1) % spinner.length;
	        }
	    } catch (InterruptedException e) {
	        System.out.println();
	        System.out.println("Spinner stopped.");
	    }
    	
    }
	
	private static void box(String _title) {
		try {
			while (true) {
				
				clearScreen();
				// config
				String msg = String.format("| State: %s | Page: %s |", sh.toString(), sh.getURL());
				String title = _title;
				// justify title center
				for (int i = 0; i < (msg.length()/2 - title.length()/4); i++) {
					title = " " + title;
				}
				System.out.println(title);
				
				// top line
				for (int i = 0; i < msg.length(); i++) {
					System.out.print("-");
				}
				System.out.println();
				
				// contents
				System.out.println(msg);
				
				// bottom line
				for (int i = 0; i < msg.length(); i++) {
					System.out.print("-");
				}
				System.out.println();
				Thread.sleep(300);
			}
		} catch (InterruptedException e) {
			System.out.println();
	        System.out.println("Box interrupted");
		}
		
	}
	
    public static void startBox(String title) {
    	Thread statusThread = new Thread(() -> {
    		box(title);
    	});

    	statusThread.setDaemon(true); 
    	statusThread.start();
    }
    
    public static void startSpinner() {
    	Thread statusThread = new Thread(() -> {
    		spinner();
    	});

    	statusThread.setDaemon(true); // set as background thread that runs until main thread stops
    	statusThread.start();
    }
	
    public static void clearScreen() {
    	for (int i = 0; i < 100; i++) {
    		System.out.println();
    	}
    }
}
