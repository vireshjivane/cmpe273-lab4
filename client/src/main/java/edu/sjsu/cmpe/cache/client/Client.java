package edu.sjsu.cmpe.cache.client;

import java.util.ArrayList;
import java.io.IOException;
import com.mashape.unirest.http.Unirest;

public class Client {

    public static void main(String[] args) throws Exception {
        
    	
    	ArrayList<CacheServiceInterface> serverList = new ArrayList<CacheServiceInterface>();
    	
	/*********************************************************************************************/
	    CacheServiceInterface cacheOne = new DistributedCacheService("http://localhost:3000");
	    CacheServiceInterface cacheTwo = new DistributedCacheService("http://localhost:3001");
	    CacheServiceInterface cacheThree = new DistributedCacheService("http://localhost:3002");
	    serverList.add(cacheOne);
	    serverList.add(cacheTwo);
	    serverList.add(cacheThree);
    	
	/*********************************************************************************************/
	    System.out.println("\nFirst Step * Started * : Write Value  - 1 => a - On Instance 1, 2 and 3");

	    CRDTClient clientCRDT = new CRDTClient(serverList);
	    clientCRDT.writeToCache(1, "a");
	    
	    System.out.println("First Step * Completed * : Write Value  - 1 => a - On Instance 1, 2 and 3");

	    System.out.println("\nTurn OFF Server A Now !\n");
	    Thread.sleep(50000);
	   
	    System.out.println("Second Step * Started *: Write Value - 1 => b - On Instance 2 and 3");	
	    clientCRDT = new CRDTClient(serverList);
	    clientCRDT.writeToCache(1, "b");
	    System.out.println("Second Step * Completed * : Write Value - 1 => b - On Instance 2 and 3");	

	    System.out.println("\nTurn ON Server A Now!\n");
	    Thread.sleep(50000);
	    System.out.println("Third Step * Started * : Read Value from Instance 1, 2 and 3");
	    clientCRDT.readCache(1);
            System.out.println("Third Step * Completed * : Read Value from Instance 1, 2 and 3");	    
	
	/*********************************************************************************************/    
	
	/* Console output for reference
		
		viresh@ubuntu:~/Desktop/cmpe273-lab4/client/bin$ ./client.sh
		
		First Step * Started * : Write Value  - 1 => a - On Instance 1, 2 and 3
		First Step * Completed * : Write Value  - 1 => a - On Instance 1, 2 and 3

		Turn OFF machine A now !

		Second Step * Started *: Write Value - 1 => b - On Instance 2 and 3
		Second Step * Completed * : Write Value - 1 => b - On Instance 2 and 3

		Turn ON machine A now !

		Third Step * Started * : Read Value from Instance 1, 2 and 3
		Third Step * Completed * : Read Value from Instance 1, 2 and 3

		Read-Repaired the value on => http://localhost:3000

		viresh@ubuntu:~/Desktop/cmpe273-lab4/client/bin$	   
	*/
	    
    }

}
