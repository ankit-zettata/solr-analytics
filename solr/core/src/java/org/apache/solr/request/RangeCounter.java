package org.apache.solr.request;

import java.io.IOException;

import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;

import org.apache.solr.request.SimpleFacets.CountPair;
import org.apache.solr.request.SimpleFacets.RangeEndpointCalculator;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SolrIndexSearcher;

public class RangeCounter {

	private Comparable start;
	private Comparable end;
	private String gap;
	
	private Comparable low;
	
	private SolrIndexSearcher searcher;
	private DocSet base;
	private SchemaField sf;
	private RangeEndpointCalculator calc;
	
	public boolean rangeHardEnd = false;
	public boolean includeLower = true;
	public boolean includeUpper = false;
	public boolean includeEdge = false;
	
	
	public RangeCounter(RangeEndpointCalculator<?> calc, Comparable<?> start, Comparable<?> end, String gap,
			 Comparable<?> low, SolrIndexSearcher searcher, DocSet base, SchemaField sf) {
		super();
		this.start = start;
		this.end = end;
		this.gap = gap;
		
		this.low = low;
		this.searcher = searcher;
		this.base = base;
		this.calc = calc;
		this.sf = sf;
	}

	public CountPair<String, Integer> getNextCount() throws IOException { 
		this.checkForCounts();
		Comparable high = calc.addGap(low, gap);
	      if (end.compareTo(high) < 0) {
	        if (rangeHardEnd) {
	          high = end;
	        } else {
	          end = high;
	        }
	      }
	      if (high.compareTo(low) < 0) {
	        throw new SolrException
	          (SolrException.ErrorCode.BAD_REQUEST,
	           "range facet infinite loop (is gap negative? did the math overflow?)");
	      }
	      
	      final boolean includeLower = 
	        (  this.includeLower ||
	          (this.includeEdge && 0 == low.compareTo(start))
	        );
	      final boolean includeUpper = 
	        (  this.includeUpper ||
	           (this.includeEdge && 0 == high.compareTo(end))
	        );
	      
	      final String lowS = calc.formatValue(low);
	      final String highS = calc.formatValue(high);

	      final int count = rangeCount(sf, lowS, highS,
	                                   includeLower,includeUpper);
	      this.low = high;
	      return new CountPair<String, Integer>(lowS, count);
	}
	
	private void checkForCounts() {
		if(!this.hasMoreCounts()) {
			throw new SolrException
				(SolrException.ErrorCode.BAD_REQUEST,
				  "range counter asked for next count but has no more count to return");
		}
	}
	
	public boolean hasMoreCounts() {
		return (low.compareTo(end) < 0);
	}
	
  
	private int rangeCount(SchemaField sf, String low, String high, boolean iLow, boolean iHigh) throws IOException {
	  Query rangeQ = sf.getType().getRangeQuery(null, sf,low,high,iLow,iHigh);
	  return searcher.numDocs(rangeQ ,base);
	}

	public Comparable getEnd() {
		// TODO Auto-generated method stub
		return this.end;
	}
	
}