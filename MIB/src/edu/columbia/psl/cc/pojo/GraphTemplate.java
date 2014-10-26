package edu.columbia.psl.cc.pojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import edu.columbia.psl.cc.datastruct.InstPool;

public class GraphTemplate {
	
	private String methodKey;
	
	private int methodArgSize;
	
	private int methodReturnSize;
	
	private boolean staticMethod;
	
	private List<InstNode> path;
	
	private InstPool pool;
	
	private HashSet<Integer> firstReadLocalVars;
	
	private HashSet<Integer> firstReadFields;
	
	private Map<String, InstNode> writeFields;
	
	public GraphTemplate() {
		
	}
	
	public GraphTemplate(GraphTemplate copy) {
		this.methodKey = copy.getMethodKey();
		this.methodArgSize = copy.getMethodArgSize();
		this.methodReturnSize = copy.getMethodReturnSize();
		this.staticMethod = copy.isStaticMethod();
		this.firstReadFields = new HashSet<Integer>(copy.getFirstReadFields());
		this.firstReadLocalVars = new HashSet<Integer>(copy.getFirstReadLocalVars());
		
		this.pool = new InstPool();
		this.path = new ArrayList<InstNode>();
		for (InstNode inst: copy.getInstPool()) {
			InstNode copyInst = new InstNode(inst);
			this.pool.add(copyInst);
		}
		
		for (int i = 0; i < copy.getPath().size(); i++) {
			InstNode pathNode = copy.getPath().get(i);
			this.path.add(this.pool.searchAndGet(pathNode.getFromMethod(), pathNode.getIdx()));
		}
		
		for (String field: copy.getWriteFields().keySet()) {
			InstNode copyNode = copy.getWriteFields().get(field);
			InstNode fieldNode = this.pool.searchAndGet(copyNode.getFromMethod(), copyNode.getIdx());
			this.writeFields.put(field, fieldNode);
		}
	}
		
	public void setMethodKey(String methodKey) {
		this.methodKey = methodKey;
	}
	
	public String getMethodKey() {
		return this.methodKey;
	}
	
	public void setPath(List<InstNode> path) {
		this.path = path;
	}
	
	public List<InstNode> getPath() {
		return this.path;
	}
	
	public void setInstPool(InstPool pool) {
		this.pool = pool;
	}
	
	public InstPool getInstPool() {
		return this.pool;
	}
	
	public void setMethodArgSize(int methodArgSize) {
		this.methodArgSize = methodArgSize;
	}
	
	public int getMethodArgSize() {
		return this.methodArgSize;
	}
	
	public void setMethodReturnSize(int methodReturnSize) {
		this.methodReturnSize = methodReturnSize;
	} 
	
	public int getMethodReturnSize() {
		return this.methodReturnSize;
	}
	
	public void setStaticMethod(boolean staticMethod) {
		this.staticMethod = staticMethod;
	}
	
	public boolean isStaticMethod() {
		return this.staticMethod;
	}
	
	public void setFirstReadLocalVars(HashSet<Integer> firstReadLocalVars) {
		this.firstReadLocalVars = firstReadLocalVars;
	}
	
	public HashSet<Integer> getFirstReadLocalVars() {
		return this.firstReadLocalVars;
	}
	
	public void setFirstReadFields(HashSet<Integer> firstReadFields) {
		this.firstReadFields = firstReadFields;
	}
	
	public HashSet<Integer> getFirstReadFields() {
		return this.firstReadFields;
	}
	
	public void setWriteFields(Map<String, InstNode> writeFields) {
		this.writeFields = writeFields;
	}
	
	public Map<String, InstNode> getWriteFields() {
		return writeFields;
	}
	
	public void showGraph() {
		for (InstNode inst: this.pool) {
			System.out.println("Parent inst: " + inst);
			
			for (String cInst: inst.getChildFreqMap().navigableKeySet()) {
				System.out.println(" " + cInst + " " + inst.getChildFreqMap().get(cInst));
			}
		}
	}

}
