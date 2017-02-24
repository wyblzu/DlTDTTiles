package com.wyb.util;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by wyb on 2017/2/17.
 * 多线程下载x-y-z格式瓦片到本地
 */
public class WebMapUtil {
    /**
     * 获取坐标点对应级别的网格行列编号
     * @param longitude 经度
     * @param latitude 纬度
     * @param level 级别
     * @return 返回行列号
     */
    private static int[] calculateCode(double longitude, double latitude, int level){
        double resolution = 360.0 / Math.pow(2, level);
        // 计算行列号
        int x = (int)(Math.ceil((180 + longitude) / resolution) - 1);
        int y = (int)(Math.ceil((90 - latitude) / resolution) - 1);
        return new int[]{x, y};
    }
    /**
     * 求取某一級別下对应范围的所有网格编号
     * @param wkt 下载范围的wkt坐标
     * @param level 级别
     * @return 所有网格编号
     */
    private static List<int[]> mapGrid(String wkt, int level){
        List<int[]> codeList = new ArrayList<>();
        try{
            Geometry geometry = new WKTReader().read(wkt);
            Coordinate[] box2d = geometry.getEnvelope().getCoordinates();
            //每次计算网格的起始点
            double x;
            double y;
            int[] code;
            double tileSize = 360/Math.pow(2, level);
            double xOrigin = box2d[0].x;
            double yOrigin = box2d[0].y;
            int xCount = (int)Math.ceil(Math.abs(box2d[2].x - box2d[0].x) / tileSize);
            int yCount = (int)Math.ceil(Math.abs(box2d[1].y - box2d[3].y) / tileSize);
            for(int i = 1; i <= xCount; i ++ ){
                for(int j = 1; j <= yCount; j ++){
                    x = xOrigin + tileSize*(i - 1);
                    y = yOrigin + tileSize*(j - 1);
                    code = calculateCode(x, y, level);
                    codeList.add(code);
                }
            }
        }catch (ParseException e){
            e.printStackTrace();
        }
        codeList = codeList.size()>0?codeList:null;
        return codeList;
    }
    /**
     *多线程下载瓦片
     * @param wkt 下载范围的wkt坐标
     * @param minZoom 下载最小级别
     * @param maxZoom 下载最大级别
     * @param urlString 下载的地址
     * @param path 存放路径
     */
    public static void downLoadTile(String wkt, int minZoom, int maxZoom, String urlString, String path){
        List<int[]> codeList;
        //开启4个线程下载
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        for(int level = minZoom; level <= maxZoom; level ++) {
            codeList = mapGrid(wkt, level);
            for(int[] codeRowCol:codeList){
                Runner runner = new Runner(codeRowCol,urlString,path,level);
                executorService.execute(runner);
            }
        }
        executorService.shutdown();
    }
    /**
     * 写入图片流
     * @param codeRowCol 行列号
     * @param urlString 下载网址
     * @param path 存放路径
     * @param level 级别
     */
    public static void writePictureStream(int[] codeRowCol,String urlString, String path, int level){
        int x = codeRowCol[0];
        int y = codeRowCol[1];
        String urlTile = urlString + "?" + "T=img_c&x=" + x + "&y=" + y + "&l=" + level;
        String tileFullPath = path + "/" + level + "/" + x + "/" + y + ".png";
        File file = new File(tileFullPath);
        //如果瓦片在本地存在，则直接返回，不再下载
        if(file.exists()) return;
        try{
            URL url = new URL(urlTile);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(5*2000);
            InputStream inputStream = urlConnection.getInputStream();
            byte[] bytes = new byte[1024];
            //按照层、列、行存储
            String outputPath = path + "/" + level + "/" + x;
            file = new File(outputPath);
            if(!file.exists()){
                file.mkdirs();
            }
            OutputStream os = new FileOutputStream(file.getPath()+"/"+y + ".png");
            int length;
            while((length = inputStream.read(bytes)) != -1){
                os.write(bytes, 0, length);
            }
            os.close();
            inputStream.close();
        }catch ( IOException e){
            if(e instanceof MalformedURLException){
                System.out.println("请检查下载网址是否正确或可用！");
            }
            e.printStackTrace();
        }
    }
}

/**
 * 线程目标对象
 */
class Runner implements Runnable{
    private int[] codeRowCol;
    private String urlString;
    private String path;
    private int level;

    //构造函数
    public Runner(int[] codeRowCol,String urlString, String path, int level){
        this.codeRowCol = codeRowCol;
        this.urlString = urlString;
        this.path = path;
        this.level = level;
    }
    @Override
    public void run(){
        WebMapUtil.writePictureStream(codeRowCol, urlString, path, level);
    }
}