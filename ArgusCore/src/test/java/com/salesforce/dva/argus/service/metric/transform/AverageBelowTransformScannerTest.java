package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricPager;
import com.salesforce.dva.argus.service.tsdb.MetricPagerTransform;
import com.salesforce.dva.argus.service.tsdb.MetricPagerValueMapping;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class AverageBelowTransformScannerTest extends AbstractTest {

	protected static MetricQuery toQuery(Metric metric) {
		TreeMap<Long, Double> datapoints = new TreeMap<>(metric.getDatapoints());
		Long start = datapoints.firstKey();
		Long end = datapoints.lastKey();
		
		return new MetricQuery(metric.getScope(), metric.getMetric(), metric.getTags(), start, end);
	}
	
	private List<MetricQuery> toQueries(List<Metric> expected) {
		List<MetricQuery> queries = new LinkedList<>();
		
		for (Metric metric : expected) {
			queries.add(toQuery(metric));
		}
		return queries;
	}
	
	private Map<MetricQuery, List<Metric>> filterOver(Metric m, Long startTime, MetricQuery query) {
		Metric mini = new Metric(m);
		Map<Long, Double> filteredDP = new TreeMap<>();
		for (Long key : m.getDatapoints().keySet()) {
			if (key >= startTime) {
				filteredDP.put(key, m.getDatapoints().get(key));
			}
		}
		mini.setDatapoints(filteredDP);
		Map<MetricQuery, List<Metric>> result = new HashMap<>();
		List<Metric> l = new ArrayList<>();
		l.add(mini);
		result.put(query, l);
		return result;
	}
	
	private Map<MetricQuery, List<Metric>> outOfBounds() {
		return new HashMap<>();
	}
	
	private Double findValue(int perc, Metric m) {
		Double[] values = new Double[m.getDatapoints().size()];
		
		values = m.getDatapoints().values().toArray(values);
		Arrays.sort(values);
		
		int ordinalRank = (int) Math.ceil(perc * values.length / 100.0);
		
		return values[ordinalRank - 1];
	}
	
	private Metric lowElems(Metric metric, Long bound) {
		Metric mini = new Metric(metric);
		Map<Long, Double> filteredDP = new TreeMap<Long, Double>();
		
		for (Long time : metric.getDatapoints().keySet()) {
			if (time <= bound) {
				filteredDP.put(time, metric.getDatapoints().get(time));
			}
		}
		mini.setDatapoints(filteredDP);
		return mini;
	}
	
	@Test
	public void testBelowFilterHighPercentage() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		List<String> constant = new ArrayList<>();
		
		int perc = 50 + random.nextInt(50); // just try to find a high percentage
		constant.add("" + findValue(perc, metrics.get(0)));
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		AverageBelowTransform transform = new AverageBelowTransform();
		
		List<Metric> expected = transform.transform(metrics, constant);
		List<Metric> actual = transform.transformScanner(scanners, constant);
		
		assert(actual.equals(expected));
	}
	
	@Test
	public void testBelowFilterLowPercentage() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		List<String> constant = new ArrayList<>();
		
		int perc = random.nextInt(50);
		constant.add("" + findValue(perc, metrics.get(0)));
		
		for (int i = 0; i < metrics.size(); i++) {
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(metrics.get(i), bound), queries.get(i), serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(metrics.get(i), bound, highQuery.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		AverageBelowTransform transform = new AverageBelowTransform();
		
		List<Metric> expected = transform.transform(metrics, constant);
		List<Metric> actual = transform.transformScanner(scanners, constant);
		
		assert(actual.equals(expected));
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		List<String> constant = new ArrayList<>();
		
		int perc = random.nextInt(50);
		constant.add("" + findValue(perc, metrics.get(0)));
		
		for (int i = 0; i < metrics.size(); i++) {
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(metrics.get(i), bound), queries.get(i), serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(metrics.get(i), bound, highQuery.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		AverageBelowTransform transform = new AverageBelowTransform();
		
		List<Metric> expected = transform.transform(metrics, constant);
		List<Metric> actual = transform.transformScanner(scanners, constant);
		
		assert(actual.equals(expected));
		
		for (int i = 0; i < expected.size(); i++) {
			assert(expected.get(i).getDatapoints().equals(actual.get(i).getDatapoints()));
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
		
	}
	
	private Metric filterUnder(Metric m, Long bound) {
		Metric res = new Metric(m);
		Map<Long, Double> dps = new HashMap<>();
		for (Long key : m.getDatapoints().keySet()) {
			if (key <= bound) {
				dps.put(key, m.getDatapoints().get(key));
			}
		}
		res.setDatapoints(dps);
		return res;
	}
	
	@Test
	public void testPager() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 5);
		List<MetricQuery> queries = toQueries(metrics);
		Long max = null;
		Long min = null;
		for (MetricQuery q : queries) {
			if (min == null || q.getStartTimestamp() < min) {
				min = q.getStartTimestamp();
			}
			if (max == null || q.getEndTimestamp() > max) {
				max = q.getEndTimestamp();
			}
		}
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<MetricQuery> upperHalf = new ArrayList<>();
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			upperHalf.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			scanners.add(new MetricScanner(filterUnder(metrics.get(i), bound), queries.get(i), serviceMock, bound));
			
			when(serviceMock.getMetrics(upperHalf)).thenReturn(filterOver(metrics.get(i), bound, upperHalf.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		Transform transform = new AverageBelowTransform();
		List<String> constants = new ArrayList<>();
		constants.add("85.0");

		List<Metric> expected = transform.transform(metrics, constants);
		Long chunkTime = (max - min) / 7;
		MetricPager stream = new MetricPagerTransform(scanners, chunkTime, transform, constants);
		
		int chunk = random.nextInt(stream.getNumberChunks());
		Long start = stream.getStartTime() + (chunk) * chunkTime;
		Long end = Math.min(start + chunkTime, stream.getEndTime());
		List<Metric> resChunk = stream.getMetricChunk(chunk);
		for (Metric m : expected) {
			TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
			if (!dps.subMap(start, end + 1).isEmpty()) {
				assert(resChunk.contains(m));
				int index = resChunk.indexOf(m);
				assert(dps.subMap(start, end + 1).equals(resChunk.get(index).getDatapoints()));
			} else {
				assert(resChunk.contains(m));
				assert(resChunk.get(resChunk.indexOf(m)).getDatapoints().isEmpty());
			}
		}
		
		List<Metric> act = stream.getMetricChunk(0);
		for (int j = 1; j < stream.getNumberChunks(); j++) {
			List<Metric> b = stream.getMetricChunk(j);
			for (Metric m : b) {
				act.get(act.indexOf(m)).addDatapoints(m.getDatapoints());
			}
		}
		
		for (Metric m : expected) {
			assert(act.contains(m));
			assert(m.getDatapoints().equals(act.get(act.indexOf(m)).getDatapoints()));
		}
	}	
}
