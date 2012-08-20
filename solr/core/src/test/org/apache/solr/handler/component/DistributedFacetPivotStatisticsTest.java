package org.apache.solr.handler.component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.solr.BaseDistributedSearchTestCase;
import org.apache.solr.client.solrj.response.PivotField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.DistributedFacetPivotTest.UnorderedEqualityArrayList;
import org.apache.solr.handler.component.DistributedFacetPivotTest.ComparablePivotField;


public class DistributedFacetPivotStatisticsTest extends
		BaseDistributedSearchTestCase {

	@Override
	public void doTest() throws Exception {

		del("*:*");
		index(id, 1, "company_s", "Lexcorp", "yearlypay_ti", "100000", "hourlypay_d", "40.00", "hiredate_dt", "2012-05-03T15:00:00Z");
		index(id, 2, "company_s", "Lexcorp", "yearlypay_ti", "110000", "hourlypay_d", "40.00", "hiredate_dt", "2012-05-03T15:00:00Z");
		index(id, 3, "company_s", "Lexcorp", "yearlypay_ti", "120000", "hourlypay_d", "40.00", "hiredate_dt", "2012-05-03T15:00:00Z");
		index(id, 4, "company_s", "Lexcorp", "yearlypay_ti", "130000", "hourlypay_d", "40.00", "hiredate_dt", "2012-05-03T15:00:00Z");
		
		index(id, 5, "company_s", "Lexcorp", "yearlypay_ti", "140000", "hourlypay_d", "50.00", "hiredate_dt", "2012-05-05T15:00:00Z");
		index(id, 6, "company_s", "Lexcorp", "yearlypay_ti", "141000", "hourlypay_d", "50.00", "hiredate_dt", "2012-05-05T15:00:00Z");
		index(id, 7, "company_s", "Lexcorp", "yearlypay_ti", "142000", "hourlypay_d", "50.00", "hiredate_dt", "2012-05-05T15:00:00Z");
		index(id, 8, "company_s", "Lexcorp", "yearlypay_ti", "143000", "hourlypay_d", "50.00", "hiredate_dt", "2012-05-05T15:00:00Z");
		index(id, 9, "company_s", "Lexcorp", "yearlypay_ti", "180000", "hourlypay_d", "50.00", "hiredate_dt", "2012-05-05T15:00:00Z");
		
		index(id, 10, "company_s", "Lexcorp", "yearlypay_ti", "190000", "hourlypay_d", "50.00", "hiredate_dt", "2012-05-30T15:00:00Z");
		
		index(id, 11, "company_s", "Stark Industries", "yearlypay_ti", "30000", "hourlypay_d", "99.50", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 12, "company_s", "Stark Industries", "yearlypay_ti", "31000", "hourlypay_d", "90.50", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 13, "company_s", "Stark Industries", "yearlypay_ti", "32000", "hourlypay_d", "91.50", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 14, "company_s", "Stark Industries", "yearlypay_ti", "33000", "hourlypay_d", "95.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 15, "company_s", "Stark Industries", "yearlypay_ti", "34000", "hourlypay_d", "95.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 16, "company_s", "Stark Industries", "yearlypay_ti", "35000", "hourlypay_d", "95.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 17, "company_s", "Stark Industries", "yearlypay_ti", "36000", "hourlypay_d", "100.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 18, "company_s", "Stark Industries", "yearlypay_ti", "37000", "hourlypay_d", "100.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 19, "company_s", "Stark Industries", "yearlypay_ti", "38500", "hourlypay_d", "100.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		index(id, 20, "company_s", "Stark Industries", "yearlypay_ti", "40000", "hourlypay_d", "100.00", "hiredate_dt", "2012-07-01T12:30:00Z");
		
		commit();
		handle.clear();
		handle.put("QTime", SKIPVAL);
		
		final ModifiableSolrParams params = new ModifiableSolrParams();
		setDistributedParams(params);
		params.add("q", "*:*");
		params.add("facet", "true");
		params.add("facet.pivot", "company_s,hiredate_dt");
		params.add(FacetParams.PERCENTILE, "true");
		params.add(FacetParams.PERCENTILE_FIELD, "yearlypay_ti");
		params.add(FacetParams.PERCENTILE_FIELD, "hourlypay_d");
		params.add(FacetParams.PERCENTILE_FIELD, "hiredate_dt");
		params.add(FacetParams.PERCENTILE_REQUESTED_PERCENTILES, "25,50,75");
		params.add("f.yearlypay_ti."+FacetParams.PERCENTILE_LOWER_FENCE, "5000");
		params.add("f.yearlypay_ti."+FacetParams.PERCENTILE_UPPER_FENCE, "500000");
		params.add("f.yearlypay_ti."+FacetParams.PERCENTILE_GAP, "1000");
		params.add("f.hourlypay_d."+FacetParams.PERCENTILE_LOWER_FENCE, "5.50");
		params.add("f.hourlypay_d."+FacetParams.PERCENTILE_UPPER_FENCE, "150.0");
		params.add("f.hourlypay_d."+FacetParams.PERCENTILE_GAP, "1.0");
		params.add("f.hiredate_dt."+FacetParams.PERCENTILE_LOWER_FENCE, "2012-05-01T00:00:00Z");
		params.add("f.hiredate_dt."+FacetParams.PERCENTILE_UPPER_FENCE, "2012-07-30T00:00:00Z");
		params.add("f.hiredate_dt."+FacetParams.PERCENTILE_GAP, "+1DAYS");
		params.add("facet.field", "hiredate_dt");
		
		QueryResponse rsp = queryServer(params);
		
		List<PivotField> expectedCompanyPivots = new UnorderedEqualityArrayList<PivotField>();
		List<PivotField> expectedLexcorpPivots = new UnorderedEqualityArrayList<PivotField>();
		List<PivotField> expectedStarkPivots = new UnorderedEqualityArrayList<PivotField>();
		
		NamedList<String> lexMay5stats_yearlypay = new NamedList<String>();
		lexMay5stats_yearlypay.add("25.0", "141500");
		lexMay5stats_yearlypay.add("50.0", "142500");
		lexMay5stats_yearlypay.add("75.0", "143500");
		NamedList<Object> lm5syp = new NamedList<Object>();
		lm5syp.add("percentiles",lexMay5stats_yearlypay);
		NamedList<String> lexMay5stats_hourlypay = new NamedList<String>();
		lexMay5stats_hourlypay.add("25.0", "050.0");
		lexMay5stats_hourlypay.add("50.0", "050.0");
		lexMay5stats_hourlypay.add("75.0", "050.0");
		NamedList<Object> lm5shp = new NamedList<Object>();
		lm5shp.add("percentiles", lexMay5stats_hourlypay);		
		NamedList<String> lexMay5stats_hiredate = new NamedList<String>();
		lexMay5stats_hiredate.add("25.0", "2012-05-05T12:00:00Z");
		lexMay5stats_hiredate.add("50.0", "2012-05-05T12:00:00Z");
		lexMay5stats_hiredate.add("75.0", "2012-05-05T12:00:00Z");
		NamedList<Object> lm5shd = new NamedList<Object>();
		lm5shd.add("percentiles", lexMay5stats_hiredate);
		SimpleOrderedMap<Object> lexMay5stats = new SimpleOrderedMap<Object>();
		lexMay5stats.add("yearlypay_ti", lm5syp);
		lexMay5stats.add("hourlypay_d", lm5shp);
		lexMay5stats.add("hiredate_dt", lm5shd);
		
		expectedLexcorpPivots.add(new ComparablePivotField("hiredate_dt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2012-05-05T15:00:00-0000"), 5, null, lexMay5stats));
		
		NamedList<String> lexMay3stats_yearlypay = new NamedList<String>();
		lexMay3stats_yearlypay.add("25.0", "100500");
		lexMay3stats_yearlypay.add("50.0", "110500");
		lexMay3stats_yearlypay.add("75.0", "120500");
		NamedList<Object> lm3syp = new NamedList<Object>();
		lm3syp.add("percentiles",lexMay3stats_yearlypay);
		NamedList<String> lexMay3stats_hourlypay = new NamedList<String>();
		lexMay3stats_hourlypay.add("25.0", "040.0");
		lexMay3stats_hourlypay.add("50.0", "040.0");
		lexMay3stats_hourlypay.add("75.0", "040.0");
		NamedList<Object> lm3shp = new NamedList<Object>();
		lm3shp.add("percentiles", lexMay3stats_hourlypay);
		NamedList<String> lexMay3stats_hiredate = new NamedList<String>();
		lexMay3stats_hiredate.add("25.0", "2012-05-03T12:00:00Z");
		lexMay3stats_hiredate.add("50.0", "2012-05-03T12:00:00Z");
		lexMay3stats_hiredate.add("75.0", "2012-05-03T12:00:00Z");
		NamedList<Object> lm3shd = new NamedList<Object>();
		lm3shd.add("percentiles", lexMay3stats_hiredate);
		SimpleOrderedMap<Object> lexMay3stats = new SimpleOrderedMap<Object>();
		lexMay3stats.add("yearlypay_ti", lm3syp);
		lexMay3stats.add("hourlypay_d", lm3shp);
		lexMay3stats.add("hiredate_dt", lm3shd);
		expectedLexcorpPivots.add(new ComparablePivotField("hiredate_dt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2012-05-03T15:00:00-0000"), 4, null, lexMay3stats));
		
		NamedList<String> lm30s_yp = new NamedList<String>();
		lm30s_yp.add("25.0", "190500");
		lm30s_yp.add("50.0", "190500");
		lm30s_yp.add("75.0", "190500");
		NamedList<Object> lm30syp = new NamedList<Object>();
		lm30syp.add("percentiles", lm30s_yp);
		NamedList<String> lm30s_hp = new NamedList<String>();
		lm30s_hp.add("25.0", "050.0");
		lm30s_hp.add("50.0", "050.0");
		lm30s_hp.add("75.0", "050.0");
		NamedList<Object> lm30shp = new NamedList<Object>();
		lm30shp.add("percentiles", lm30s_hp);
		NamedList<String> lm30s_hd = new NamedList<String>();
		lm30s_hd.add("25.0", "2012-05-30T12:00:00Z");
		lm30s_hd.add("50.0", "2012-05-30T12:00:00Z");
		lm30s_hd.add("75.0", "2012-05-30T12:00:00Z");
		NamedList<Object> lm30shd = new NamedList<Object>();
		lm30shd.add("percentiles", lm30s_hd);
		SimpleOrderedMap<Object> lexMay30stats = new SimpleOrderedMap<Object>();
		lexMay30stats.add("yearlypay_ti", lm30syp);
		lexMay30stats.add("hourlypay_d", lm30shp);
		lexMay30stats.add("hiredate_dt", lm30shd);
		expectedLexcorpPivots.add(new ComparablePivotField("hiredate_dt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2012-05-30T15:00:00-0000"), 1, null, lexMay30stats ));
		
		NamedList<String> lms_yp = new NamedList<String>();
		lms_yp.add("25.0", "120500");
		lms_yp.add("50.0", "140500");
		lms_yp.add("75.0", "143500");
		NamedList<Object> lmsyp = new NamedList<Object>();
		lmsyp.add("percentiles", lms_yp);
		NamedList<String> lms_hp = new NamedList<String>();
		lms_hp.add("25.0", "040.0");
		lms_hp.add("50.0", "050.0");
		lms_hp.add("75.0", "050.0");
		NamedList<Object> lmshp = new NamedList<Object>();
		lmshp.add("percentiles", lms_hp);
		NamedList<String> lms_hd = new NamedList<String>();
		lms_hd.add("25.0", "2012-05-03T12:00:00Z");
		lms_hd.add("50.0", "2012-05-05T12:00:00Z");
		lms_hd.add("75.0", "2012-05-05T12:00:00Z");
		NamedList<Object> lmshd = new NamedList<Object>();
		lmshd.add("percentiles", lms_hd);
		SimpleOrderedMap<Object> lexcorpStats = new SimpleOrderedMap<Object>();
		lexcorpStats.add("yearlypay_ti", lmsyp);
		lexcorpStats.add("hourlypay_d", lmshp);
		lexcorpStats.add("hiredate_dt", lmshd);
		expectedCompanyPivots.add(new ComparablePivotField("company_s", "Lexcorp", 10, expectedLexcorpPivots, lexcorpStats));
		
		NamedList<String> tonyJuly1stats_yearlypay = new NamedList<String>();
		tonyJuly1stats_yearlypay.add("25.0", "032500");
		tonyJuly1stats_yearlypay.add("50.0", "034500");
		tonyJuly1stats_yearlypay.add("75.0", "037500");
		NamedList<Object> tj1syp = new NamedList<Object>();
		tj1syp.add("percentiles", tonyJuly1stats_yearlypay);
		NamedList<String> tj1s_hp = new NamedList<String>();
		tj1s_hp.add("25.0", "095.0");
		tj1s_hp.add("50.0", "095.0");
		tj1s_hp.add("75.0", "100.0");
		NamedList<Object> tj1shp = new NamedList<Object>();
		tj1shp.add("percentiles", tj1s_hp);
		NamedList<String> tj1s_hd = new NamedList<String>();
		tj1s_hd.add("25.0", "2012-07-01T12:00:00Z");
		tj1s_hd.add("50.0", "2012-07-01T12:00:00Z");
		tj1s_hd.add("75.0", "2012-07-01T12:00:00Z");
		NamedList<Object> tj1shd = new NamedList<Object>();
		tj1shd.add("percentiles", tj1s_hd);
		SimpleOrderedMap<Object> tonyJuly1stats = new SimpleOrderedMap<Object>();
		tonyJuly1stats.add("yearlypay_ti", tj1syp);
		tonyJuly1stats.add("hourlypay_d", tj1shp);
		tonyJuly1stats.add("hiredate_dt", tj1shd);
		expectedStarkPivots.add(new ComparablePivotField("hiredate_dt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2012-07-01T12:30:00-0000"), 10, null, tonyJuly1stats ));
		expectedCompanyPivots.add(new ComparablePivotField("company_s", "Stark Industries", 10, expectedStarkPivots, tonyJuly1stats));
		
		List<PivotField> companyPivots = rsp.getFacetPivot().get("company_s,hiredate_dt");
		assertEquals(expectedCompanyPivots, companyPivots);
		
		//test averages
		params.add(FacetParams.PERCENTILE_AVERAGES, "true");
		lm5syp.add("percentiles_average", 149700d);
		lm5syp.add("percentiles_count", 5);				
		lm5shp.add("percentiles_average", 50.0d);
		lm5shp.add("percentiles_count", 5);		
		lm5shd.add("percentiles_average", 0d);
		lm5shd.add("percentiles_count", 5);
		
		lm3syp.add("percentiles_average", 115500d);
		lm3syp.add("percentiles_count", 4);				
		lm3shp.add("percentiles_average", 40.0d);
		lm3shp.add("percentiles_count", 4);		
		lm3shd.add("percentiles_average", 0d);
		lm3shd.add("percentiles_count", 4);

		lm30syp.add("percentiles_average", 190500d);
		lm30syp.add("percentiles_count", 1);				
		lm30shp.add("percentiles_average", 50.0d);
		lm30shp.add("percentiles_count", 1);		
		lm30shd.add("percentiles_average", 0d);
		lm30shd.add("percentiles_count", 1);
		
		lmsyp.add("percentiles_average", 140100d);
		lmsyp.add("percentiles_count", 10);				
		lmshp.add("percentiles_average", 46.0d);
		lmshp.add("percentiles_count", 10);		
		lmshd.add("percentiles_average", 0d);
		lmshd.add("percentiles_count", 10);
		
		tj1syp.add("percentiles_average", 35100d);
		tj1syp.add("percentiles_count", 10);
		tj1shp.add("percentiles_average", 96.8d);
		tj1shp.add("percentiles_count", 10);
		tj1shd.add("percentiles_average", 0d);
		tj1shd.add("percentiles_count", 10);

		rsp = queryServer(params);
		companyPivots = rsp.getFacetPivot().get("company_s,hiredate_dt");
		assertEquals(expectedCompanyPivots, companyPivots);
		
	}

}