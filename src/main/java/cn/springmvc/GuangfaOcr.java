package cn.springmvc;


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.ImageHelper;
import net.sourceforge.tess4j.util.LoadLibs;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;

public class GuangfaOcr {
	
    private static int getGray(int rgb){
		String str=Integer.toHexString(rgb);
		int r=Integer.parseInt(str.substring(2,4),16);
		int g=Integer.parseInt(str.substring(4,6),16);
		int b=Integer.parseInt(str.substring(6,8),16);
		//or 直接new个color对象
		Color c=new Color(rgb);
		r=c.getRed();
	    	g=c.getGreen();
		b=c.getBlue();
		int top=(r+g+b)/3;
		return (int)(top);
	}
	
	/**
	 * 自己加周围8个灰度值再除以9，算出其相对灰度值
	 * @param gray
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @return
	 */
	private static int  getAverageColor(int[][] gray, int x, int y, int w, int h)
    {
        int rs = gray[x][y]
                      	+ (x == 0 ? 255 : gray[x - 1][y])
			            + (x == 0 || y == 0 ? 255 : gray[x - 1][y - 1])
			            + (x == 0 || y == h - 1 ? 255 : gray[x - 1][y + 1])
			            + (y == 0 ? 255 : gray[x][y - 1])
			            + (y == h - 1 ? 255 : gray[x][y + 1])
			            + (x == w - 1 ? 255 : gray[x + 1][ y])
			            + (x == w - 1 || y == 0 ? 255 : gray[x + 1][y - 1])
			            + (x == w - 1 || y == h - 1 ? 255 : gray[x + 1][y + 1]);
        return rs / 9;
    }
	
    private static BufferedImage erzhihuaImage(BufferedImage image) {
		int h=image.getHeight();//获取图像的高
		int w=image.getWidth();//获取图像的宽
//		int rgb=image.getRGB(0, 0);//获取指定坐标的ARGB的像素值
		int[][] gray=new int[w][h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				gray[x][y]=getGray(image.getRGB(x, y));
			}
		}
		
		BufferedImage nbi=new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		int SW=146;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if(getAverageColor(gray, x, y, w, h)>SW){
					int max=new Color(255,255,255).getRGB();
					nbi.setRGB(x, y, max);
				}else{
					int min=new Color(0,0,0).getRGB();
					nbi.setRGB(x, y, min);
				}
			}
		}
		return nbi;
	}
	
	private static int colorToRGB(int alpha, int red, int green, int blue) {
		int newPixel = 0;
		newPixel += alpha;
		newPixel = newPixel << 8;
		newPixel += red;
		newPixel = newPixel << 8;
		newPixel += green;
		newPixel = newPixel << 8;
		newPixel += blue;
		return newPixel;
	}
	
	private static BufferedImage grayImage(BufferedImage image)
			throws IOException {
		BufferedImage grayImage = new BufferedImage(image.getWidth(),
				image.getHeight(), image.getType());
		for (int i = 0; i < image.getWidth(); i++) {
			for (int j = 0; j < image.getHeight(); j++) {
				final int color = image.getRGB(i, j);
				final int r = (color >> 16) & 0xff;
				final int g = (color >> 8) & 0xff;
				final int b = color & 0xff;
				int gray = (int) (0.3 * r + 0.59 * g + 0.11 * b);
				;
				int newPixel = colorToRGB(255, gray, gray, gray);
				grayImage.setRGB(i, j, newPixel);
			}
		}
		return grayImage;
	}
	
	private static boolean isBlackColor(int colorInt) {
		Color color = new Color(colorInt);
		int blue = color.getBlue();
		int green = color.getGreen();
		int red = color.getRed();
		if(Math.abs(blue-green)<22
				&&Math.abs(blue-red)<22
				&&Math.abs(green-red)<22
				){
			return true;
		}else{
			return false;
		}
	}
	
	
    // 4.判断字体的颜色含义：正常可以用rgb三种颜色加起来表示，字与非字应该有显示的区别，找出来。
    private static boolean isFontColor(int colorInt) {
        Color color = new Color(colorInt);

        return color.getRed() + color.getGreen() + color.getBlue() == 340;
    }
	
    // 取得指定位置的颜色是否为白色，如果超出边界，返回true
    // 本方法是从removeInterference方法中摘取出来的。单独调用本方法无意义。
    private static boolean isWhiteColor(BufferedImage image, int x, int y) throws Exception {
        if(x < 0 || y < 0) return true;
        if(x >= image.getWidth() || y >= image.getHeight()) return true;

        Color color = new Color(image.getRGB(x, y));
        
        return color.equals(Color.WHITE)?true:false;
    }
	
    // 2.去除图像干扰像素（非必须操作，只是可以提高精度而已）。
    public static BufferedImage removeInterference(BufferedImage image)  
            throws Exception {  
        int width = image.getWidth();  
        int height = image.getHeight();  
        for (int x = 0; x < width; ++x) {  
            for (int y = 0; y < height; ++y) {  
                if (isBlackColor(image.getRGB(x, y))) {
                    image.setRGB(x, y, Color.WHITE.getRGB());  
                }
            }  
        }  
        return image;  
     }
    
    public static String OcrImage(InputStream instream){
    	String result=null;
    	try {
    		BufferedImage read = ImageIO.read(instream);
			read = removeInterference(read);
			read = grayImage(read);
			read = erzhihuaImage(read);
			Tesseract instance = new Tesseract(); // JNA Interface Mapping
			instance.setTessVariable("tessedit_char_whitelist", "0123456789abcdefghijklmnopqrstuvwxyz");
			File tessDataFolder = LoadLibs.extractTessResources("tessdata"); // Maven build only; only English data bundled
			instance.setDatapath(tessDataFolder.getAbsolutePath());
			result = instance.doOCR(read);
			result = StringUtils.replace(StringUtils.trim(result), " ", "");
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return result;
    }
    
    
	
	public static void main(String[] args) throws Exception {
		//System.out.println(OcrImage(new FileInputStream(new File("F:\\travel\\yzm\\yzm.png"))));;
        HttpClient httpClient = new DefaultHttpClient();
            String url = "http://travel.ceair.com/validateCode.vld?28";
            HttpGet getMethod = new HttpGet(url);
            try {
                HttpResponse response = httpClient.execute(getMethod, new BasicHttpContext());
                HttpEntity entity = response.getEntity();
                InputStream instream = entity.getContent(); 
                OutputStream outstream = new FileOutputStream(new File("F:\\travel\\yzm\\yzm.png"));
                byte[] tmp = new byte[2048]; 
                while ((instream.read(tmp)) != -1) {
                    outstream.write(tmp);
                } 
              
              String reslut =  OcrImage(new FileInputStream(new File("F:\\travel\\yzm\\yzm.png")));
              System.out.println(reslut);
                
        		//BufferedImage read = ImageIO.read(instream);
        		//ImageIO.write(read, "jpg", new File("F:\\travel\\yzm\\yzm_1.png"));
                BufferedImage read = ImageIO.read(new ByteArrayInputStream(tmp));
        		BufferedImage read2 = removeInterference(read);
        		read2 = grayImage(read2);
        		read2 = erzhihuaImage(read2);
       		ImageIO.write(read2, "png", new File("F:\\travel\\yzm\\yzm_chuli.png"));
            } finally {
                getMethod.releaseConnection();
        }

		
		
		

		
		
	}
}