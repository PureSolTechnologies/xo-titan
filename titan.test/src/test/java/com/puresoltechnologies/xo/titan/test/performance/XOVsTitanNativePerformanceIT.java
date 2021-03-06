package com.puresoltechnologies.xo.titan.test.performance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.buschmais.xo.api.ConcurrencyMode;
import com.buschmais.xo.api.Transaction;
import com.buschmais.xo.api.ValidationMode;
import com.buschmais.xo.api.XOManager;
import com.buschmais.xo.api.XOManagerFactory;
import com.buschmais.xo.api.bootstrap.XO;
import com.buschmais.xo.api.bootstrap.XOUnit;
import com.puresoltechnologies.xo.titan.impl.TitanStoreSession;
import com.puresoltechnologies.xo.titan.test.XOTitanTestUtils;
import com.puresoltechnologies.xo.titan.test.relation.typed.TreeNode;
import com.puresoltechnologies.xo.titan.test.relation.typed.TreeNodeRelation;
import com.thinkaurelius.titan.core.TitanGraph;
import com.tinkerpop.blueprints.Vertex;

public class XOVsTitanNativePerformanceIT {

	private static XOManagerFactory xoManagerFactory;

	private static final int TREE_DEPTH = 6;
	private static final int NUMBER_OF_RUNS = 5;

	private class Measurement {

		private final long counter;
		private final long duration;
		private final double speed;

		public Measurement(long counter, long start, long stop) {
			super();
			this.counter = counter;
			this.duration = stop - start;
			speed = (counter * 1000.0) / duration;
		}

		public long getCounter() {
			return counter;
		}

		public long getDuration() {
			return duration;
		}

		public double getSpeed() {
			return speed;
		}

	}

	@BeforeClass
	public static void initialize() {
		Collection<XOUnit[]> xoUnits = XOTitanTestUtils.xoUnits(Arrays
				.<Class<?>> asList(TreeNode.class, TreeNodeRelation.class),
				Collections.<Class<?>> emptyList(), ValidationMode.NONE,
				ConcurrencyMode.MULTITHREADED,
				Transaction.TransactionAttribute.MANDATORY);
		assertThat(xoUnits, hasSize(1));
		XOUnit[] xoUnit = xoUnits.iterator().next();
		assertThat(xoUnit.length, is(1));
		xoManagerFactory = XO.createXOManagerFactory(xoUnit[0]);

		try (XOManager xoManager = xoManagerFactory.createXOManager()) {
			TitanStoreSession datastoreSession = xoManager
					.getDatastoreSession(TitanStoreSession.class);
			TitanGraph titanGraph = datastoreSession.getTitanGraph();
			// Some initial input to finish bootstrapping...
			Vertex vertex1 = titanGraph.addVertex(null);
			Vertex vertex2 = titanGraph.addVertex(null);
			vertex1.addEdge("BOOTSTRAPPING", vertex2);
		}
	}

	@AfterClass
	public static void destroy() {
		xoManagerFactory.close();
	}

	private final List<Measurement> xoMeasurements = new ArrayList<>();
	private final List<Measurement> nativeMeasurements = new ArrayList<>();

	@Test
	public void test() {
		for (int i = 0; i < NUMBER_OF_RUNS; i++) {
			runWithXO();
		}
		for (int i = 0; i < NUMBER_OF_RUNS; i++) {
			runNative();
		}
		printResults();
	}

	public void runWithXO() {
		XOTitanTestUtils.clearTitanKeyspace(xoManagerFactory.getXOUnit());
		try (XOManager xoManager = xoManagerFactory.createXOManager()) {

			long start = System.currentTimeMillis();

			xoManager.currentTransaction().begin();
			TreeNode root = xoManager.create(TreeNode.class);
			root.setName("1");
			xoManager.currentTransaction().commit();

			long counter = 1;
			counter += addChildrenXO(xoManager, root, 2, "1");
			long stop = System.currentTimeMillis();

			Measurement measurement = new Measurement(counter, start, stop);

			System.err.println("counter=" + measurement.getCounter());
			System.err.println("time=" + measurement.getDuration() + "ms");
			System.err.println("speed=" + measurement.getSpeed()
					+ " vertizes/s");
			xoMeasurements.add(measurement);
		}
	}

	private long addChildrenXO(XOManager xoManager, TreeNode parent, int i,
			String namePrefix) {
		if (i > TREE_DEPTH) {
			return 0;
		}
		long counter = 0;
		for (int id = 1; id <= i; id++) {
			String name = namePrefix + id;

			xoManager.currentTransaction().begin();
			TreeNode child = xoManager.create(TreeNode.class);
			child.setName(name);
			xoManager.create(parent, TreeNodeRelation.class, child);
			counter++;
			xoManager.currentTransaction().commit();

			counter += addChildrenXO(xoManager, child, i + 1, name);
		}
		return counter;
	}

	public void runNative() {
		XOTitanTestUtils.clearTitanKeyspace(xoManagerFactory.getXOUnit());
		try (XOManager xoManager = xoManagerFactory.createXOManager()) {
			TitanStoreSession datastoreSession = xoManager
					.getDatastoreSession(TitanStoreSession.class);
			TitanGraph titanGraph = datastoreSession.getTitanGraph();

			long start = System.currentTimeMillis();

			Vertex root = titanGraph.addVertex(null);
			root.setProperty("name", "1");
			titanGraph.commit();

			long counter = 1;
			counter += addChildrenNative(titanGraph, root, 2, "1");
			long stop = System.currentTimeMillis();

			Measurement measurement = new Measurement(counter, start, stop);

			System.err.println("counter=" + measurement.getCounter());
			System.err.println("time=" + measurement.getDuration() + "ms");
			System.err.println("speed=" + measurement.getSpeed()
					+ " vertizes/s");
			nativeMeasurements.add(measurement);
		}
	}

	private long addChildrenNative(TitanGraph titanGraph, Vertex parent, int i,
			String namePrefix) {
		if (i > TREE_DEPTH) {
			return 0;
		}
		long counter = 0;
		for (int id = 1; id <= i; id++) {
			String name = namePrefix + id;

			Vertex child = titanGraph.addVertex(null);
			child.setProperty("name", name);
			parent.addEdge("treeNodeRelation", child);
			counter++;
			titanGraph.commit();

			counter += addChildrenNative(titanGraph, child, i + 1, name);
		}
		return counter;
	}

	private void printResults() {
		System.out.println("===========");
		System.out.println("XO Results:");
		System.out.println("===========");
		print(xoMeasurements);
		System.out.println();
		System.out.println("===============");
		System.out.println("Native Results:");
		System.out.println("===============");
		print(nativeMeasurements);
	}

	private void print(List<Measurement> measurements) {
		System.out.println("Counter\tDuration [ms]\tSpeed [vertices/s]");
		System.out
				.println("--------------------------------------------------");
		long durationSum = 0;
		double speedSum = 0.0;
		for (Measurement measurement : measurements) {
			long counter = measurement.getCounter();
			long duration = measurement.getDuration();
			double speed = measurement.getSpeed();
			String format = MessageFormat.format("{0}\t{1}\t{2,number,#.##}",
					counter, duration, speed);
			System.out.println(format);
			speedSum += speed;
			durationSum += duration;
		}
		System.out
				.println("--------------------------------------------------");
		double durationAvg = (double) durationSum
				/ (double) measurements.size();
		System.out.println(MessageFormat.format(
				"average duration={0,number,#.##} ms", durationAvg));
		double speedAvg = speedSum / measurements.size();
		System.out.println(MessageFormat.format(
				"average speed={0,number,#.##} vertices/s", speedAvg));
	}

	public static void main(String[] args) throws InterruptedException {
		Thread.sleep(3000);
		initialize();
		try {
			new XOVsTitanNativePerformanceIT().test();
		} finally {
			destroy();
		}
	}
}
