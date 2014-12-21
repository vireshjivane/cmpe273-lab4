package edu.sjsu.cmpe.cache.client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import javax.print.attribute.standard.Severity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.Future;

public class CRDTClient {

	public int successCount = 0;
	public int failureCount = 0;
	public int writeCount = 0;
	public int addKey;
	public int delSuccess = -1;
	public String oldValue;

	
	public ArrayList<CacheServiceInterface> serverList;
	public ArrayList<CacheServiceInterface> writeFailedServers;
	public ArrayList<CacheServiceInterface> successServerList = new ArrayList<CacheServiceInterface>();	
	public static HashMap<String, Integer> savedReadValue = new HashMap<String, Integer>();
	public HashMap<String, String> valuesByServers = new HashMap<String, String>();

	CRDTClient(ArrayList<CacheServiceInterface> serverList) 
	
	{
		this.serverList = serverList;
	}

/****************************************************************************************************************/

	public void writeToCache(int key, String value) throws IOException 
	{

		boolean writeSuccess = false;
		addKey = key;

		this.successCount = 0;
		this.failureCount = 0;

		this.writeCount = serverList.size();

		savedIntermediateState();

		this.successCount = 0;
		this.failureCount = 0;

		this.writeCount = serverList.size();

		this.successServerList = new ArrayList<CacheServiceInterface>();

		writeFailedServers = new ArrayList<CacheServiceInterface>();

		for (final CacheServiceInterface server : serverList) 
		{
			HttpResponse<JsonNode> res = null;
			try {
				res = Unirest
						.put(server.toString() + "/cache/{key}/{value}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.routeParam("value", value)
						.asJsonAsync(new Callback<JsonNode>(){

							@Override
							public void failed(UnirestException e) 
							{
								writeCount--;
								failureCount++;
								callbackWrite();
								writeFailedServers.add(server);
							}

							@Override
							public void completed(HttpResponse<JsonNode> res) 
							{
								if (res.getCode() != 200) 
								{
									writeCount--;
									failureCount++;
								} 
								else 
								{
									writeCount--;
									successCount++;
									successServerList.add(server);
								}
								callbackWrite();
							}

							@Override
							public void cancelled() 
							{
								writeCount--;
								failureCount++;
								callbackWrite();

							}
						}).get();
			} catch (Exception e) {	}

			if (res == null || res.getCode() != 200) { }

		}


	}

/****************************************************************************************************************/

	public void readCache(int key) throws IOException 
	{   

		boolean writeSuccess = false;
		addKey = key;

		this.successCount = 0;
		this.failureCount = 0;
		this.writeCount = serverList.size();

		for (final CacheServiceInterface server : serverList) 
		{
			Future<HttpResponse<JsonNode>> res = null;
			String tempServer = server.toString();
			try {
				res = Unirest
						.get(server.toString() + "/cache/{key}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.asJsonAsync(new Callback<JsonNode>() {

							@Override
							public void failed(UnirestException e) {
								writeCount--;
								failureCount++;
								callbackRead();

							}

							@Override
							public void completed(HttpResponse<JsonNode> res) 
							{
								writeCount--;
								successCount++;
								String value = "";
								if(res.getBody() != null)
								{
									value = res.getBody().getObject().getString("value");
								}
								valuesByServers.put(server.toString(), value);
								Integer getExistingCounter = savedReadValue.get(value);
								if(getExistingCounter == null)
								{
									savedReadValue.put(value, 1);
								}else
								{
									savedReadValue.put(value, getExistingCounter+1);
								}
								callbackRead();
							}

							@Override
							public void cancelled() 
							{
								writeCount--;
								failureCount++;
								callbackRead();
							}
						});
			} catch (Exception e) {	e.printStackTrace(); }


		}
	}

/****************************************************************************************************************/


	public void deleteFromCache(int key) throws IOException{ 

		this.successCount = 0;
		this.failureCount = 0;
		this.writeCount = successServerList.size();

		for (CacheServiceInterface server : successServerList) 
		{
			HttpResponse<JsonNode> res = null;

			try {
				Unirest
				.delete(server.toString() + "/cache/{key}")
				.header("accept", "application/json")
				.routeParam("key", Long.toString(key))
				.asJsonAsync(new Callback<JsonNode>() {

					@Override
					public void failed(UnirestException e) {
						writeCount--;
						failureCount++;
						callbackDelete();

					}

					@Override
					public void completed(HttpResponse<JsonNode> res) 
					{
						if (res.getCode() != 204) 
						{
							writeCount--;
							failureCount++;
						} 
						else 
						{
							writeCount--;
							successCount++;
						}
						callbackDelete();

					}

					@Override
					public void cancelled() 
					{
						writeCount--;
						failureCount++;
						callbackDelete();

					}
				});

			} catch (Exception e) {	System.err.println(e); }

		}

	}

/****************************************************************************************************************/

	public void callbackWrite() 
	{

		if (writeCount == 0 && failureCount >= 2) 
		{

			try {
				System.out.println("Failed write - Rolling back values ! "+addKey+" => "+oldValue);
				deleteFromCache(addKey);

			} catch (IOException e) { e.printStackTrace(); }
		}

	}

/****************************************************************************************************************/

	public void callbackDelete() {

		if (writeCount == 0 && failureCount == 0) {
			delSuccess++;
			writeFailedRollback();
		}else{
			try {
				deleteFromCache(addKey);
			} catch (IOException e) { e.printStackTrace(); }
		}

	}

/****************************************************************************************************************/

	public void callbackRead() {

		if (writeCount == 0 && successCount == 3) 
		{
			String repairVal = getRepairVal();
			repairCache(repairVal);

		}
		else{ }

	}

/****************************************************************************************************************/

	
	public void repairCache(String repairVal){

		this.successCount = 0;
		this.failureCount = 0;
		this.writeCount = successServerList.size();

		ArrayList<CacheServiceInterface> updateServerList = new ArrayList<CacheServiceInterface>();

		for (Entry<String, String> server : valuesByServers.entrySet()) {

			if(server.getValue() != null && !server.getValue().equals(repairVal)){

				updateServerList.add(new DistributedCacheService(server.getKey().toString()));
			}

		}

		if(updateServerList.size() >  0){

			for(CacheServiceInterface server : updateServerList){
				System.out.println("\nRead-Repaired the value on => "+ server.toString() + "\n");
				try {
					HttpResponse<JsonNode> res = Unirest.put(server.toString() + "/cache/{key}/{value}")
							.header("accept", "application/json")
							.routeParam("key", Long.toString(addKey))
							.routeParam("value", repairVal)
							.asJson();
				} catch (UnirestException e) {	e.printStackTrace(); }
			}

		}else{

		}
		try {
			Unirest.shutdown();
		} catch (IOException e) { e.printStackTrace(); }
	}

/****************************************************************************************************************/


	private String getRepairVal(){

		MapComparator mapComparator = new MapComparator(savedReadValue);
		SortedMap<String, Integer> sortedMap = new TreeMap<String, Integer>(mapComparator);
		sortedMap.putAll(savedReadValue);
		return sortedMap.firstKey();

	}

/****************************************************************************************************************/

	private void savedIntermediateState(){
		for(CacheServiceInterface server:serverList){
			try {
				HttpResponse<JsonNode> res = Unirest.get(server + "/cache/{key}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(addKey)).asJson();
				oldValue = res.getBody().getObject().getString("value");
				if(oldValue == null) { continue; }
				else { break; }
			} catch (Exception e) {	continue; }
		}
	}

/****************************************************************************************************************/

	private void writeFailedRollback(){

		for(CacheServiceInterface successServerList : this.successServerList)
		{
			String prevValue = oldValue;

			try {
				HttpResponse<JsonNode> res = Unirest.put(successServerList.toString() + "/cache/{key}/{value}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(addKey))
						.routeParam("value", prevValue)
						.asJson();
			     } catch (Exception e) {	}
		}

	}

/****************************************************************************************************************/

}
