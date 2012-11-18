package org.apache.solr.request;

import java.util.ArrayList;
import java.util.Collections;

import org.apache.lucene.search.FieldCache.DocTermsIndex;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SimpleFacets.CountPair;
import org.apache.solr.schema.FieldType;
import org.apache.solr.util.BoundedTreeSet;

public class FacetPercentiles {
	
	private int runningCount = 0;
	private ArrayList<Double> targetCounts = new ArrayList<Double>();
	private ArrayList<Double> requestedPercentiles = new ArrayList<Double>();
	private int nextTargetCountIndex = 0;
	private NamedList<Integer> buckets = new NamedList<Integer>();
	private NamedList<String> percentiles;
	private double average = 0;
	private double total = 0;
	private int totalCount = 0;

	public double getAverage() {
		return average;
	}

	public int getTotalCount() {
		return totalCount;
	}
	
	public double getTotal() {
	  return total;
	}
	
	public FacetPercentiles(String[] requestedPercentiles, int totalCount) {
			    
		for (String individualRequestedPercentileSet : requestedPercentiles){      	
			for(String individualRequestedPercentile : individualRequestedPercentileSet.split(",")) {
				try{
					double requestedPercentile = Double.parseDouble(individualRequestedPercentile);
					double targetCount =  totalCount * (requestedPercentile / 100);
					this.requestedPercentiles.add(requestedPercentile);
					this.targetCounts.add(targetCount);      	
				}      	
				catch (Exception e){
				}
			}
		}
		Collections.sort(this.requestedPercentiles);
		Collections.sort(this.targetCounts);
		percentiles = new NamedList<String>();
		this.totalCount = totalCount;
	}	
	
	public void processFacetCount(String facetValue, int count) {		
		if (stillLookingForPercentiles()) {
			runningCount += count;
			while (stillLookingForPercentiles() && isFacetValueInteresting()){		
				recordFoundPercentile(facetValue);
			}
		}
	}
	
	public void accumulateAverage(String facetValue, int count) {
	  double val = 0;
	  try {
	    val = Double.parseDouble(facetValue);
	  } catch (Exception e) { }
	  average = average + (val * count/totalCount);
	  total += val * count;
	}
	
	public void storeFacetCount(String facetValue, int count) {
		buckets.add(facetValue,count);
	}
	
	private boolean isFacetValueInteresting() {
		return targetCounts.get(nextTargetCountIndex) <= runningCount;
	}

	private void recordFoundPercentile(String facetValue) {
		percentiles.add(requestedPercentiles.get(nextTargetCountIndex).toString(), facetValue);
		nextTargetCountIndex++;		
	}

	public boolean foundAllPercentiles() {
		return nextTargetCountIndex >= targetCounts.size();
	}
	
	public boolean stillLookingForPercentiles() {
		return !foundAllPercentiles();
	}
	
	public NamedList<String> getPercentiles() {
		return percentiles;
	}
	
	public NamedList<Integer> getBuckets() {
		return this.buckets;
	}
}