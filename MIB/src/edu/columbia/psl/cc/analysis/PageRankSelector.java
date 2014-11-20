package edu.columbia.psl.cc.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections15.Transformer;
import org.apache.log4j.Logger;

import com.google.gson.reflect.TypeToken;

import edu.columbia.psl.cc.config.MIBConfiguration;
import edu.columbia.psl.cc.datastruct.InstPool;
import edu.columbia.psl.cc.pojo.GraphTemplate;
import edu.columbia.psl.cc.pojo.HotZone;
import edu.columbia.psl.cc.pojo.InstNode;
import edu.columbia.psl.cc.util.GraphUtil;
import edu.columbia.psl.cc.util.GsonManager;
import edu.columbia.psl.cc.util.SearchUtil;
import edu.columbia.psl.cc.util.StringUtil;
import edu.columbia.psl.cc.util.TemplateLoader;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.algorithms.scoring.PageRankWithPriors;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Hypergraph;


public class PageRankSelector {
	
	private static Logger logger = Logger.getLogger(PageRankSelector.class);
	
	private static double alpha = MIBConfiguration.getInstance().getPgAlpha();
	
	private static int maxIteration = MIBConfiguration.getInstance().getPgMaxIter();
	
	private static double epsilon = MIBConfiguration.getInstance().getPgEpsilon();
	
	private static int instLimit = MIBConfiguration.getInstance().getInstLimit();
	
	private static double simThreshold = MIBConfiguration.getInstance().getSimThreshold();
	
	private static String header = "template,test,pgrank_template,c_template,ct_line,s_test,s_line,c_test,c_line,e_test,e_line,seg_size,dist,similarity\n";
	
	private static Comparator<InstWrapper> pageRankSorter = new Comparator<InstWrapper>() {
		public int compare(InstWrapper i1, InstWrapper i2) {
			if (i1.pageRank < i2.pageRank) {
				return 1;
			} else if (i1.pageRank > i2.pageRank) {
				return -1;
			} else {
				if (i1.inst.getOp().getOpcode() > i2.inst.getOp().getOpcode()) {
					return 1;
				} else if (i1.inst.getOp().getOpcode() < i2.inst.getOp().getOpcode()) {
					return -1;
				} else {
					return 0;
				}
			}
		}
	};
	
	private static double levenSimilarity(int dist, int base) {
		double sim = 1 - ((double)dist/base);
		return sim;
	}
	
	private InstPool myPool;
	
	private HashMap<InstNode, Double> priors;
	
	private boolean partialPool;
	
	public PageRankSelector(InstPool myPool, boolean partialPool) {
		this.myPool = myPool;
		this.partialPool = partialPool;
	}
	
	/**
	 * Key is opcode, double is their prior
	 * @param priors
	 */
	public void setPriors(HashMap<InstNode, Double> priors) {
		this.priors = priors;
	}
	
	public DirectedSparseGraph<InstNode, Integer> convertToJungGraph() {
		DirectedSparseGraph<InstNode, Integer> retGraph = new DirectedSparseGraph<InstNode, Integer>();
		
		int edgeId = 0;
		HashMap<String, InstNode> cache = new HashMap<String, InstNode>();
		for (InstNode inst: myPool) {
			retGraph.addVertex(inst);
			
			for (String childKey: inst.getChildFreqMap().keySet()) {
				InstNode childNode = null;
				if (cache.containsKey(childKey)) {
					childNode = cache.get(childKey);
				} else {
					String[] keys = StringUtil.parseIdxKey(childKey);
					childNode = myPool.searchAndGet(keys[0], 
							Long.valueOf(keys[1]), Integer.valueOf(keys[2]), Integer.valueOf(keys[3]));
				}
				
				if (!partialPool)
					retGraph.addEdge(new Integer(edgeId++), inst, childNode);
				else {
					if (childNode != null)
						retGraph.addEdge(new Integer(edgeId++), inst, childNode);
				}
			}
		}
		
		return retGraph;
	}
	
	public List<InstWrapper> computePageRank() {
		Hypergraph<InstNode, Integer> jungGraph = this.convertToJungGraph();
		logger.info("Vertex size: " + jungGraph.getVertexCount());
		logger.info("Edge size: " + jungGraph.getEdgeCount());
		
		PageRankWithPriors<InstNode, Integer> ranker = null;
		if (this.priors == null) {
			logger.info("Rank withoug priors");
			ranker = new PageRank<InstNode, Integer>(jungGraph, alpha);
		} else {
			logger.info("Rank with priors");
			Transformer<InstNode, Double> transformer = new Transformer<InstNode, Double>() {
				@Override
				public Double transform(InstNode inst) {
					double prior = priors.get(inst);
					return prior;
				}
			};
			ranker = new PageRankWithPriors<InstNode, Integer>(jungGraph, transformer, alpha);
		}
		
		List<InstWrapper> rankList = new ArrayList<InstWrapper>();
		ranker.setMaxIterations(maxIteration);
		ranker.setTolerance(epsilon);
		ranker.evaluate();
		
		for (InstNode inst: jungGraph.getVertices()) {
			InstWrapper iw = new InstWrapper(inst, ranker.getVertexScore(inst));
			rankList.add(iw);
		}
		
		Collections.sort(rankList, pageRankSorter);
		return rankList;
	}
	
	public InstPool selectRepPool() {
		InstPool ret = new InstPool();
		List<InstWrapper> sorted = this.computePageRank();
		for (int i = 0; i < instLimit; i++) {
			ret.add(sorted.get(i).inst);
		}
		return ret;
	}
	
	public static HashMap<InstNode, List<InstNode>> locateSegments(HashSet<InstNode> assignments, 
			List<InstNode> sortedTarget, 
			int before, 
			int after) {
		HashMap<InstNode, List<InstNode>> candSegs = new HashMap<InstNode, List<InstNode>>();
		for (InstNode inst: assignments) {
			List<InstNode> seg = new ArrayList<InstNode>();
			
			for (int i = 0; i < sortedTarget.size(); i++) {
				InstNode curNode = sortedTarget.get(i);
				if (curNode.equals(inst)) {
					//collect backward
					int start = i - before;
					if (start < 0)
						start = 0;
					
					int end = i + after;
					if (end > sortedTarget.size() - 1)
						end = sortedTarget.size() - 1;
					
					seg.addAll(sortedTarget.subList(start, end + 1));
					break ;
				}
			}
			candSegs.put(inst, seg);
		}
		return candSegs;
	}
	
	public static List<HotZone> subGraphSearch(GraphProfile subProfile, GraphTemplate targetGraph) {
		List<InstNode> sortedTarget = GraphUtil.sortInstPool(targetGraph.getInstPool(), true);
		
		HashSet<InstNode> miAssignments = SearchUtil.possibleSingleAssignment(subProfile.centroidWrapper.inst, targetGraph);
		logger.info("Target graph: " + targetGraph.getMethodKey());
		logger.info("Possible assignments: " + miAssignments);
		HashMap<InstNode, List<InstNode>> candSegs = locateSegments(miAssignments, sortedTarget, subProfile.before, subProfile.after);
		List<HotZone> hits = new ArrayList<HotZone>();
		
		for (InstNode cand: candSegs.keySet()) {
			List<InstNode> segments = candSegs.get(cand);
			InstPool segPool = new InstPool();
			segPool.addAll(segments);
			
			PageRankSelector ranker = new PageRankSelector(segPool, true);
			List<InstWrapper> ranks = ranker.computePageRank();
			int[] candPGRep = SearchUtil.generatePageRankRep(ranks);
			
			int dist = 0;
			if (candPGRep.length == 0) {
				dist = subProfile.pgRep.length;
			} else {
				dist = LevenshteinDistance.calculateSimilarity(subProfile.pgRep, candPGRep);
			}
			
			double sim = levenSimilarity(dist, subProfile.pgRep.length);
			
			if (sim >= simThreshold) {
				HotZone zone = new HotZone();
				zone.setSubCentroid(subProfile.centroidWrapper.inst);
				zone.setSubPgRank(subProfile.centroidWrapper.pageRank);
				zone.setStartInst(segments.get(0));
				zone.setCentralInst(cand);
				zone.setEndInst(segments.get(segments.size() - 1));
				zone.setLevDist(dist);
				zone.setSimilarity(sim);
				zone.setSegs(segPool);
				hits.add(zone);
			}
		}
		return hits;
	}
	
	public static GraphProfile profileGraph(GraphTemplate subGraph) {
		if (subGraph.getInstPool().size() == 0) {
			return null;
		}
		List<InstNode> sortedSub = GraphUtil.sortInstPool(subGraph.getInstPool(), true);
		
		//Pick the most important node from sorteSob
		PageRankSelector subSelector = new PageRankSelector(subGraph.getInstPool(), false);
		List<InstWrapper> subRank = subSelector.computePageRank();
		int[] subPGRep = SearchUtil.generatePageRankRep(subRank);
		logger.info("Sub graph profile: " + subGraph.getMethodKey());
		logger.info("Sub graph PageRank: " + Arrays.toString(subPGRep));
		
		//Use the most important inst as the central to collect insts in target
		InstNode subCentroid = subRank.get(0).inst;
		int before = 0, after = 0;
		boolean recordBefore = true;
		for (int i = 0; i < sortedSub.size(); i++) {
			InstNode curNode = sortedSub.get(i);
			
			if (curNode.equals(subCentroid)) {
				recordBefore = false;
				continue ;
			}
			
			if (recordBefore) {
				before++;
			} else {
				after++;
			}
		}
		
		GraphProfile gp = new GraphProfile();
		gp.centroidWrapper = subRank.get(0);
		gp.before = before;
		gp.after = after;
		gp.pgRep = subPGRep;
		
		return gp;
	}
	
	public static void initiateSubGraphMining(String templateDir, String testDir) {		
		StringBuilder sb = new StringBuilder();
		sb.append(header);
		TypeToken<GraphTemplate> graphToken = new TypeToken<GraphTemplate>(){};
		
		HashMap<String, GraphTemplate> templates = null;
		HashMap<String, GraphTemplate> tests = null;
		
		boolean probeTemplate = TemplateLoader.probeDir("./template");
		boolean probeTest = TemplateLoader.probeDir("./tests");
		if (probeTemplate && probeTest) {
			logger.info("Comparison mode: templates vs tests");
			templates = TemplateLoader.loadTemplate(new File("./template"), graphToken);
			tests = TemplateLoader.loadTemplate(new File("./test"), graphToken);
		} else if (probeTemplate) {
			logger.info("Exhaustive mode: templates vs. templates");
			templates = TemplateLoader.loadTemplate(new File("./template"), graphToken);
			tests = TemplateLoader.loadTemplate(new File("./template"), graphToken);
		} else if (probeTest) {
			logger.info("Exhaustive mode: tests vs. tests");
			templates = TemplateLoader.loadTemplate(new File("./test"), graphToken);
			tests = TemplateLoader.loadTemplate(new File("./test"), graphToken);
		} else {
			logger.info("Empty repos for both templates and tests");
			return ;
		}
		
		for (String templateName: templates.keySet()) {
			StringBuilder rawRecorder = new StringBuilder();
			
			GraphTemplate tempGraph = templates.get(templateName);
			
			if (tempGraph.getInstPool().size() < MIBConfiguration.getInstance().getInstThreshold()) {
				continue ;
			}
			
			GraphUtil.removeReturnInst(tempGraph.getInstPool());
			GraphProfile tempProfile = profileGraph(tempGraph);
			if (tempProfile == null) {
				logger.warn("Empty graph: " + tempGraph.getMethodKey());
				continue ;
			}
			
			logger.info("Template name: " + tempGraph.getMethodKey());
			logger.info("Inst node size: " + tempGraph.getInstPool().size());
			
			ExecutorService executor = Executors.newFixedThreadPool(MIBConfiguration.getInstance().getParallelFactor());
			HashMap<String, Future<List<HotZone>>> resultRecorder = 
					new HashMap<String, Future<List<HotZone>>>();
			for (String testName: tests.keySet()) {
				if (testName.equals(templateName)) {
					continue ;
				}
				
				GraphTemplate testGraph = tests.get(testName);
				//GraphUtil.removeReturnInst(testGraph.getInstPool());
				logger.info("Test name: " + testGraph.getMethodKey());
				logger.info("Inst node size: " + testGraph.getInstPool().size());
				
				SubGraphCrawler crawler = new SubGraphCrawler();
				crawler.subGraphProfile = tempProfile;
				crawler.targetGraph = testGraph;
				
				Future<List<HotZone>> hits = executor.submit(crawler);
				resultRecorder.put(testName, hits);
			}
			
			executor.shutdown();
			while (!executor.isTerminated());
			System.out.println("Subgraph crawling is completed for: " + templateName);
			
			try {
				for (String testName: resultRecorder.keySet()) {
					List<HotZone> zones = resultRecorder.get(testName).get();
					
					for (HotZone hit: zones) {
						logger.info("Start inst: " + hit.getStartInst());
						logger.info("Centroid inst: " + hit.getCentralInst());
						logger.info("End inst: " + hit.getEndInst());
						logger.info("Distance: " + hit.getLevDist());
						logger.info("Similarity: " + hit.getSimilarity());
						
						rawRecorder.append(templateName + 
								"," + testName + 
								"," + hit.getSubPgRank() +
								"," + hit.getSubCentroid() + 
								"," + hit.getSubCentroid().getLinenumber() +
								"," + hit.getStartInst() + 
								"," + hit.getStartInst().getLinenumber() + 
								"," + hit.getCentralInst() + 
								"," + hit.getCentralInst().getLinenumber() +
								"," + hit.getEndInst() + 
								"," + hit.getEndInst().getLinenumber() +
								"," + hit.getSegs().size() + 
								"," + hit.getLevDist() + 
								"," + hit.getSimilarity() + "\n");
					}
				}
			} catch (Exception ex) {
				logger.error(ex);
			}
			sb.append(rawRecorder.toString());
		}
		GsonManager.writeResult(sb);
	}
				
	public static void main(String[] args) {
		String templateDir = MIBConfiguration.getInstance().getTemplateDir();
		String testDir = MIBConfiguration.getInstance().getTestDir();
				
		logger.info("Start PageRank analysis for Bytecode subgraph mining");
		logger.info("Similarity threshold: " + simThreshold);
		logger.info("Alpha: " + alpha);
		logger.info("Max iteration: " + maxIteration);
		logger.info("Epsilon: " + epsilon);
		
		initiateSubGraphMining(templateDir, testDir);
	}
	
	private static class GraphProfile {
		InstWrapper centroidWrapper;
		
		int before;
		
		int after; 
		
		int[] pgRep;
	}
	
	private static class SubGraphCrawler implements Callable<List<HotZone>>{
		GraphProfile subGraphProfile;
		
		GraphTemplate targetGraph;
		
		@Override
		public List<HotZone> call() throws Exception {
			List<HotZone> hits = subGraphSearch(subGraphProfile, targetGraph);
			return hits;
		}
	}
}