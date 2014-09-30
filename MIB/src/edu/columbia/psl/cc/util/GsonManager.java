package edu.columbia.psl.cc.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import edu.columbia.psl.cc.pojo.Var;

public class GsonManager {
	
	private static String templateDir = "./template";
	
	private static String testDir = "./test";
	
	private static String pathDir = "./path";
	
	private static String labelmapDir = "./labelmap";
	
	public static void writePath(String fileName, List<String> path) {
		StringBuilder sb = new StringBuilder();
		for (String inst: path) {
			sb.append(inst + "\n");
		}
		
		try {
			File f = new File(pathDir + "/" + fileName + ".txt");
			if (f.exists())
				f.delete();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			bw.write(sb.toString());
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static <T> void writeJson(T obj, String fileName, boolean isTemplate) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		gb.registerTypeAdapter(Var.class, new VarAdapter());
		Gson gson = gb.create();
		String toWrite = gson.toJson(obj);
		try {
			File f;
			if (isTemplate) {
				f = new File(templateDir + "/" + fileName + ".json");
			} else {
				f = new File(testDir + "/" + fileName + ".json");
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			bw.write(toWrite);
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static <T> T readJson(File f, T type) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		gb.registerTypeAdapter(Var.class, new VarAdapter());
		Gson gson = gb.create();
		try {
			JsonReader jr = new JsonReader(new FileReader(f));
			T ret = gson.fromJson(jr, type.getClass());
			jr.close();
			return ret;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 0 for template, 1 for test, 2 for labelmap
	 * @param obj
	 * @param fileName
	 * @param typeToken
	 * @param isTemplate
	 */
	public static <T> void writeJsonGeneric(T obj, String fileName, TypeToken typeToken, int dirIdx) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		Gson gson = gb.create();
		String toWrite = gson.toJson(obj, typeToken.getType());
		try {
			File f;
			if (dirIdx == 0) {
				f = new File(templateDir + "/" + fileName + ".json");
			} else if (dirIdx == 1) {
				f = new File(testDir + "/" + fileName + ".json");
			} else {
				f = new File(labelmapDir + "/" + fileName + ".json");
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			bw.write(toWrite);
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static <T> T readJsonGeneric(File f, TypeToken typeToken) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		Gson gson = gb.create();
		try {
			JsonReader jr = new JsonReader(new FileReader(f));
			T ret = gson.fromJson(jr, typeToken.getType());
			jr.close();
			return ret;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	private static void cleanHelper(String fileName) {
		File dir = new File(fileName);
		if (!dir.isDirectory()) {
			dir.delete();
		} else {
			for (File f: dir.listFiles()) {
				cleanHelper(f.getAbsolutePath());
			}
		}
	}
	
	public static void cleanDirs() {
		File tempDir = new File(templateDir);
		File teDir = new File(testDir);
		boolean hasToDelete = true;
		if (!tempDir.isDirectory()) {
			tempDir.delete();
			tempDir.mkdir();
			hasToDelete = false;
		}
		
		if (!teDir.isDirectory()) {
			teDir.delete();
			teDir.mkdir();
			hasToDelete = false;
		}
		
		if (hasToDelete) {
			cleanHelper(tempDir.getAbsolutePath());
			cleanHelper(teDir.getAbsolutePath());
		}
	}

}
