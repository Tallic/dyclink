package edu.columbia.psl.cc.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;

import com.google.gson.reflect.TypeToken;

public class TemplateLoader {

	public static <T> HashMap<String, T> loadTemplate(File dir, TypeToken<T> typeToken) {
		HashMap<String, T> ret = new HashMap<String, T>();
		if (!dir.isDirectory()) {
			T temp = GsonManager.readJsonGeneric(dir, typeToken);
			ret.put(dir.getName(), temp);
		} else {
			FilenameFilter filter = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(".json");
				}
			};
			
			for (File f: dir.listFiles(filter)) {
				String name = f.getName().replace(".json", "");
				T value = GsonManager.readJsonGeneric(f, typeToken);
				ret.put(name, value);
			}
		}
		return ret;
	}

}
