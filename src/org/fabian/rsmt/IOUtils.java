package org.fabian.rsmt;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class IOUtils {
	public static byte[] readFile(String file) throws IOException{
		FileInputStream fis = new FileInputStream(file);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buff = new byte[1024];
		int read;
		while ((read = fis.read(buff)) != -1) {
			baos.write(buff, 0, read);
		}
		baos.flush();
		fis.close();
		byte[] dat = baos.toByteArray();
		baos.close();
		return dat;
	}
}
