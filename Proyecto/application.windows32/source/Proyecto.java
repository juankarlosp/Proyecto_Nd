import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import gab.opencv.*; 
import java.awt.Rectangle; 
import processing.video.*; 
import controlP5.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class Proyecto extends PApplet {

/**
 * Basado en Image Filtering
 * Funciona para Processing 2
 
 * It requires the ControlP5 Processing library:
 * http://www.sojamo.de/libraries/controlP5/
 */






OpenCV opencv;
Capture video;
PImage src, fondo, processedImage, contoursImage, background;

ArrayList<Contour> contours;
// List of detected contours parsed as blobs (every frame)
ArrayList<Contour> newBlobContours;

// List of my blob objects (persistent)
ArrayList<Blob> blobList;
// Number of blobs detected over all time. Used to set IDs.
int blobCount = 0;

// List of conexiones objects
ArrayList<Conexion> conexionList;

float contrast = 0.90f;
int brightness = 0;
int threshold = 90;
boolean useAdaptiveThreshold = false; // use basic thresholding
int thresholdBlockSize = 489;
int thresholdConstant = 90;
int blobSizeThreshold = 30;
int blurSize = 4;
int centros[][] = new int[50][4];
boolean calibrar=true;

// Control vars
ControlP5 cp5;
int buttonColor;
int buttonBgColor;

public void setup() {
  frameRate(20);

  video = new Capture(this, 640, 480);
  video.start();
  fondo= createImage(640, 480, RGB);

  opencv = new OpenCV(this, 640, 480);
  contours = new ArrayList<Contour>();
  //personas = new ArrayList<Persona>();
  // Blobs list
  blobList = new ArrayList<Blob>();
  conexionList = new ArrayList<Conexion>();
  //size(opencv.width + 200, opencv.height, P2D);
  size(1280, 720, P2D);
  // Init Controls
  cp5 = new ControlP5(this);
  initControls();

  // Set thresholding
  toggleAdaptiveThreshold(useAdaptiveThreshold);
  background = loadImage("fondo.jpg");
}

public void draw() {

  // Read last captured frame
  if (video.available()) {
    video.read();
  }

  // Load the new frame of our camera in to OpenCV
  opencv.loadImage(video);
  src = opencv.getSnapshot();
  opencv.diff(fondo);

  ///////////////////////////////
  // <1> PRE-PROCESS IMAGE
  // - Grey channel 
  // - Brightness / Contrast
  ///////////////////////////////

  // Gray channel
  //opencv.gray();

  //opencv.brightness(brightness);
  opencv.contrast(contrast);
  // Save snapshot for display
  //preProcessedImage = opencv.getSnapshot();

  ///////////////////////////////
  // <2> PROCESS IMAGE
  // - Threshold
  // - Noise Supression
  ///////////////////////////////

  // Adaptive threshold - Good when non-uniform illumination
  if (useAdaptiveThreshold) {

    // Block size must be odd and greater than 3
    if (thresholdBlockSize%2 == 0) thresholdBlockSize++;
    if (thresholdBlockSize < 3) thresholdBlockSize = 3;

    opencv.adaptiveThreshold(thresholdBlockSize, thresholdConstant);

    // Basic threshold - range [0, 255]
  } else {
    opencv.threshold(threshold);
  }

  // Invert (black bg, white blobs)
  //opencv.invert();

  // Reduce noise - Dilate and erode to close holes
  opencv.dilate();
  opencv.erode();

  // Blur
  opencv.blur(blurSize);

  // Save snapshot for display
  processedImage = opencv.getSnapshot();

  ///////////////////////////////
  // <3> FIND CONTOURS  
  ///////////////////////////////
  detectBlobs();
  // Passing 'true' sorts them by descending area.
  //contours = opencv.findContours(true, true);

  // Save snapshot for display
  contoursImage = opencv.getSnapshot();

  // Draw
  //Si estamos calibrando
  if (calibrar) {
    pushMatrix();

    // Leave space for ControlP5 sliders
    translate(width-src.width, 0);

    // Display images
    displayImages();

    // Display contours in the lower right window
    pushMatrix();
    scale(0.5f);
    translate(src.width, src.height);

    //displayContours();
    displayContoursCenters();
    for (int i = 0; i < centros.length; i++) {
      int xx = centros[i][0];
      int yy = centros[i][1];
      int xw = centros[i][2];
      int yh = centros[i][3];

      ellipse(xx+xw/2, yy+yh/2, 15, 15);
    }
    popMatrix(); 

    popMatrix();
  }

  //si estamos presentando
  else {
    image(background, 0, 0);
    //displayContoursCenters();
    detectConexion();
    displayConexiones();
    displayBlobs();
  }
}

/////////////////////
// Display Methods
/////////////////////

public void displayImages() {
  pushMatrix();
  scale(0.5f);
  image(src, 0, 0);
  image(fondo, src.width, 0);
  image(processedImage, 0, src.height);
  image(src, src.width, src.height);
  popMatrix();

  stroke(255);
  fill(255);
  text("Source", 10, 25); 
  text("Pre-processed Image", src.width/2 + 10, 25); 
  text("Processed Image", 10, src.height/2 + 25); 
  text("Tracked Points", src.width/2 + 10, src.height/2 + 25);
}
public void displayBlobs() {

  for (Blob b : blobList) {
    //strokeWeight(1);
    b.display();
  }
}
public void displayConexiones() {

  for (Conexion cc : conexionList) {
    cc.pintarConexion();
  }
}
public void displayContours() {

  for (int i=0; i<contours.size (); i++) {

    Contour contour = contours.get(i);

    noFill();
    stroke(0, 255, 0);
    strokeWeight(3);
    contour.draw();
  }
}

public void displayContoursCenters() {

  for (int i=0; i<contours.size (); i++) {

    Contour contour = contours.get(i);
    Rectangle r = contour.getBoundingBox();
    int[][] valores;

    if (//(contour.area() > 0.9 * src.width * src.height) ||
    (r.width < blobSizeThreshold || r.height < blobSizeThreshold))
      continue;
    centros[i][0]=r.x;
    centros[i][1]=r.y;
    centros[i][2]=r.width;
    centros[i][3]=r.height;
  }
  return;
}
////////////////////
// Blob Detection
////////////////////

public void detectBlobs() {

  // Contours detected in this frame
  // Passing 'true' sorts them by descending area.
  contours = opencv.findContours(true, true);

  newBlobContours = getBlobsFromContours(contours);

  //println(contours.length);

  // Check if the detected blobs already exist are new or some has disappeared. 

  // SCENARIO 1 
  // blobList is empty
  if (blobList.isEmpty()) {
    // Just make a Blob object for every face Rectangle
    for (int i = 0; i < newBlobContours.size (); i++) {
      //println("+++ New blob detected with ID: " + blobCount);
      blobList.add(new Blob(this, blobCount, newBlobContours.get(i)));
      blobCount++;
    }

    // SCENARIO 2 
    // We have fewer Blob objects than face Rectangles found from OpenCV in this frame
  } else if (blobList.size() <= newBlobContours.size()) {
    boolean[] used = new boolean[newBlobContours.size()];
    // Match existing Blob objects with a Rectangle
    for (Blob b : blobList) {
      // Find the new blob newBlobContours.get(index) that is closest to blob b
      // set used[index] to true so that it can't be used twice
      float record = 50000;
      int index = -1;
      for (int i = 0; i < newBlobContours.size (); i++) {
        float d = dist(newBlobContours.get(i).getBoundingBox().x, newBlobContours.get(i).getBoundingBox().y, b.getBoundingBox().x, b.getBoundingBox().y);
        //float d = dist(blobs[i].x, blobs[i].y, b.r.x, b.r.y);
        if (d < record && !used[i]) {
          record = d;
          index = i;
        }
      }
      // Update Blob object location
      used[index] = true;
      b.update(newBlobContours.get(index));
    }
    // Add any unused blobs
    for (int i = 0; i < newBlobContours.size (); i++) {
      if (!used[i]) {
        println("+++ New blob detected with ID: " + blobCount);
        blobList.add(new Blob(this, blobCount, newBlobContours.get(i)));
        //blobList.add(new Blob(blobCount, blobs[i].x, blobs[i].y, blobs[i].width, blobs[i].height));
        blobCount++;
      }
    }

    // SCENARIO 3 
    // We have more Blob objects than blob Rectangles found from OpenCV in this frame
  } else {
    // All Blob objects start out as available
    for (Blob b : blobList) {
      b.available = true;
    } 
    // Match Rectangle with a Blob object
    for (int i = 0; i < newBlobContours.size (); i++) {
      // Find blob object closest to the newBlobContours.get(i) Contour
      // set available to false
      float record = 50000;
      int index = -1;
      for (int j = 0; j < blobList.size (); j++) {
        Blob b = blobList.get(j);
        float d = dist(newBlobContours.get(i).getBoundingBox().x, newBlobContours.get(i).getBoundingBox().y, b.getBoundingBox().x, b.getBoundingBox().y);
        //float d = dist(blobs[i].x, blobs[i].y, b.r.x, b.r.y);
        if (d < record && b.available) {
          record = d;
          index = j;
        }
      }
      // Update Blob object location
      Blob b = blobList.get(index);
      b.available = false;
      b.update(newBlobContours.get(i));
    } 
    // Start to kill any left over Blob objects
    for (Blob b : blobList) {
      if (b.available) {
        b.countDown();
        if (b.dead()) {
          b.delete = true;
        }
      }
    }
  }

  // Delete any blob that should be deleted
  for (int i = blobList.size ()-1; i >= 0; i--) {
    Blob b = blobList.get(i);
    if (b.delete) {
      blobList.remove(i);
    }
  }
}

public ArrayList<Contour> getBlobsFromContours(ArrayList<Contour> newContours) {

  ArrayList<Contour> newBlobs = new ArrayList<Contour>();

  // Which of these contours are blobs?
  for (int i=0; i<newContours.size (); i++) {

    Contour contour = newContours.get(i);
    Rectangle r = contour.getBoundingBox();

    if (//(contour.area() > 0.9 * src.width * src.height) ||
    (r.width < blobSizeThreshold || r.height < blobSizeThreshold))
      continue;

    newBlobs.add(contour);
  }

  return newBlobs;
}

////////////////////
// Conexion Detection
////////////////////

public void detectConexion() {
  println("Tama\u00f1o de blobList: " + blobList.size());
  println("Tama\u00f1o de conexionList: " + conexionList.size());
  if (blobList.size()>=3) {
    if (conexionList.isEmpty()) {
      for (int i = 0; i < blobList.size ()-2; i++) {
        for (int o = i+1; o < blobList.size ()-1; o++) {
          for (int p = i+2; p < blobList.size (); p++) {
            Blob b1 = blobList.get(i);
            int[] b1c=b1.getCoords();
            Blob b2 = blobList.get(o);
            int[] b2c=b2.getCoords();
            Blob b3 = blobList.get(p);
            int[] b3c=b3.getCoords();
            conexionList.add(new Conexion(b1.id, b2.id, b3.id, b1c[0], b1c[1], b2c[0], b2c[1], b3c[0], b3c[1]));
          }
        }
      }
    } 
    else {
      for (int i = 0; i < blobList.size()-2; i++) {
        for (int o = i+1; o < blobList.size()-1; o++) {
          for (int p = i+2; p < blobList.size(); p++) {
            Blob b1 = blobList.get(i);
            int[] b1c=b1.getCoords();
            Blob b2 = blobList.get(o);
            int[] b2c=b2.getCoords();
            Blob b3 = blobList.get(p);
            int[] b3c=b3.getCoords();
            boolean existe=false;
            for (int u = 0; u < conexionList.size(); u++) {
              Conexion con = conexionList.get(u);
              if ((con.id[0]==b1.id)&&(con.id[1]==b2.id)&&(con.id[2]==b3.id)) {
                con.actualizarConexion(b1c[0], b1c[1], b2c[0], b2c[1], b3c[0], b3c[1]);
                existe=true;
              }
            }
            if(existe==false){
            conexionList.add(new Conexion(b1.id, b2.id, b3.id, b1c[0], b1c[1], b2c[0], b2c[1], b3c[0], b3c[1]));
            }
          }
        }
      }
    }
  }
  //Limpiar conexiones que no existen
  int cantBlobsActuales=0;
  for (int i = conexionList.size ()- 1; i >= 0; i--) {
    Conexion con = conexionList.get(i);
    int conexionID [] = new int[3];
    conexionID=con.id;
    for (int j = 0; j < blobList.size (); j++) {
      Blob blobActual = blobList.get(j);
      int idBlob=blobActual.id;
      if((idBlob==conexionID[0])||(idBlob==conexionID[1])||(idBlob==conexionID[2])){
        cantBlobsActuales++;
      }
    }
    if(cantBlobsActuales<3){
      conexionList.remove(i);
      println("Eliminando conexion: " + i);
    }
  }
}
//////////////////////////
// CONTROL P5 Functions
//////////////////////////

public void initControls() {
  // Slider for contrast
  cp5.addSlider("contrast")
    .setLabel("contrast")
      .setPosition(20, 50)
        .setRange(0.0f, 6.0f)
          ;

  // Slider for threshold
  cp5.addSlider("threshold")
    .setLabel("threshold")
      .setPosition(20, 110)
        .setRange(0, 255)
          ;

  // Toggle to activae adaptive threshold
  cp5.addToggle("toggleAdaptiveThreshold")
    .setLabel("use adaptive threshold")
      .setSize(10, 10)
        .setPosition(20, 144)
          ;

  // Slider for adaptive threshold block size
  cp5.addSlider("thresholdBlockSize")
    .setLabel("a.t. block size")
      .setPosition(20, 180)
        .setRange(1, 700)
          ;

  // Slider for adaptive threshold constant
  cp5.addSlider("thresholdConstant")
    .setLabel("a.t. constant")
      .setPosition(20, 200)
        .setRange(-100, 100)
          ;

  // Slider for blur size
  cp5.addSlider("blurSize")
    .setLabel("blur size")
      .setPosition(20, 260)
        .setRange(1, 20)
          ;

  // Slider for minimum blob size
  cp5.addSlider("blobSizeThreshold")
    .setLabel("min blob size")
      .setPosition(20, 290)
        .setRange(0, 60)
          ;

  // Store the default background color, we gonna need it later
  buttonColor = cp5.getController("contrast").getColor().getForeground();
  buttonBgColor = cp5.getController("contrast").getColor().getBackground();
}

public void toggleAdaptiveThreshold(boolean theFlag) {

  useAdaptiveThreshold = theFlag;

  if (useAdaptiveThreshold) {

    // Lock basic threshold
    setLock(cp5.getController("threshold"), true);

    // Unlock adaptive threshold
    setLock(cp5.getController("thresholdBlockSize"), false);
    setLock(cp5.getController("thresholdConstant"), false);
  } else {

    // Unlock basic threshold
    setLock(cp5.getController("threshold"), false);

    // Lock adaptive threshold
    setLock(cp5.getController("thresholdBlockSize"), true);
    setLock(cp5.getController("thresholdConstant"), true);
  }
}

public void setLock(Controller theController, boolean theValue) {

  theController.setLock(theValue);

  if (theValue) {
    theController.setColorBackground(color(150, 150));
    theController.setColorForeground(color(100, 100));
  } else {
    theController.setColorBackground(color(buttonBgColor));
    theController.setColorForeground(color(buttonColor));
  }
}


/////////////////////
// Capturar fondo
/////////////////////

public void keyPressed() {
  if (key == CODED) {
    if (keyCode == DOWN) {
      calibrar=true;
      cp5.show();
    } else if (keyCode == UP) {
      calibrar=false;
      cp5.hide();
    }
  } else if ((key == ENTER)||(key == RETURN)) {
    fondo=src;
  }
  /*for (int i = blobList.size() - 1; i >= 0; i--) {
        blobList.remove(i);
    }
    for (int i = conexionList.size() - 1; i >= 0; i--) {
        conexionList.remove(i);
    }*/
}

/**
 * Blob Class
 *
 * Based on this example by Daniel Shiffman:
 * http://shiffman.net/2011/04/26/opencv-matching-faces-over-time/
 * 
 * @author: Jordi Tost (@jorditost)
 * 
 * University of Applied Sciences Potsdam, 2014
 */

class Blob {
  
  private PApplet parent;
  
  // Contour
  public Contour contour;
  
  // Am I available to be matched?
  public boolean available;
  
  // Should I be deleted?
  public boolean delete;
  
  // How long should I live if I have disappeared?
  private int initTimer = 10; //127;
  public int timer;
  
  // Unique ID for each blob
  public int id;
  int tamano;
  
  // Make me
  Blob(PApplet parent, int id, Contour c) {
    this.parent = parent;
    this.id = id;
    this.contour = new Contour(parent, c.pointMat);
    
    available = true;
    delete = false;
    
    timer = initTimer;
    tamano=round(random(25,60));
    
  }
  
  // Show me
  public void display() {
    Rectangle r = contour.getBoundingBox();
    fill(0,0,0);
    noStroke();
    int posx=r.x+r.width/2;
    int posy=r.y+r.height/2;
    posx = round(map(posx, 0, 640, width, 0));
    posy = round(map(posy, 0, 480, 0, height));
    ellipse(posx, posy, tamano, tamano);
  }
  
  public int[] getCoords() {
    Rectangle r = contour.getBoundingBox();
    int[] t = new int[2];
    int posx=r.x+r.width/2;
    int posy=r.y+r.height/2;
    posx = round(map(posx, 0, 640, width, 0));
    posy = round(map(posy, 0, 480, 0, height));
    t[0]=posx;
    t[1]=posy;
    return t;
  }

  // Give me a new contour for this blob (shape, points, location, size)
  // Oooh, it would be nice to lerp here!
  public void update(Contour newC) {
    
    contour = new Contour(parent, newC.pointMat);
    
    // Is there a way to update the contour's points without creating a new one?
    /*ArrayList<PVector> newPoints = newC.getPoints();
    Point[] inputPoints = new Point[newPoints.size()];
    
    for(int i = 0; i < newPoints.size(); i++){
      inputPoints[i] = new Point(newPoints.get(i).x, newPoints.get(i).y);
    }
    contour.loadPoints(inputPoints);*/
    
    timer = initTimer;
  }

  // Count me down, I am gone
  public void countDown() {    
    timer--;
  }

  // I am deed, delete me
  public boolean dead() {
    if (timer < 0) return true;
    return false;
  }
  
  public Rectangle getBoundingBox() {
    return contour.getBoundingBox();
  }
}
class Conexion {
  int links;
  int pos1X;
  int pos1Y;
  int pos2X;
  int pos2Y;
  int pos3X;
  int pos3Y;
  int vectores[];
  int movvectores[];
  int col;
  int[] id = new int[3];

  Conexion(int ident1, int ident2, int ident3, int pos1x, int pos1y, int pos2x, int pos2y, int pos3x, int pos3y) {
    id[0]=ident1;
    id[1]=ident2;
    id[2]=ident3;
    pos1X=pos1x;
    pos1Y=pos1y;
    pos2X=pos2x;
    pos2Y=pos2y;
    pos3X=pos3x;
    pos3Y=pos3y;
    vectores=new int[12];
    movvectores=new int[12];
    for (int v=0; v < vectores.length; v++) {
      vectores[v] = round(random(-200, 200));
      movvectores[v] = round(random(-5, 5));
    }
    int c=round(random(.5f, 5.4f));
    if (c==1) {
      col = color(209, 73, 52);
    }
    if (c==2) {
      col = color(255, 248, 65);
    }
    if (c==3) {
      col = color(48, 85, 146);
    }
    if (c==4) {
      col = color(92, 124, 78);
    }
    if (c==5) {
      col = color(20, 18, 27);
    }
  }
  //FUNCIONES
  public void pintarConexion(){
    fill(col);
    stroke(0);
    strokeWeight(5);
    beginShape();
    vertex(pos1X, pos1Y);
    bezierVertex(pos2X+vectores[0], pos2Y+vectores[1], pos2X+vectores[2], pos2Y+vectores[3], pos2X, pos2Y);
    bezierVertex(pos3X+vectores[4], pos3Y+vectores[5], pos3X+vectores[6], pos3Y+vectores[7], pos3X, pos3Y);
    bezierVertex(pos1X+vectores[8], pos1Y+vectores[9], pos1X+vectores[10], pos1Y+vectores[11], pos1X, pos1Y);
    endShape();
    moverConexion();
  }
  public void actualizarConexion(int pos1x, int pos1y, int pos2x, int pos2y, int pos3x, int pos3y){
    pos1X=pos1x;
    pos1Y=pos1y;
    pos2X=pos2x;
    pos2Y=pos2y;
    pos3X=pos3x;
    pos3Y=pos3y;
  }
  public void moverConexion() {
    for (int v=0; v < vectores.length; v++) {
      vectores[v]=vectores[v]+movvectores[v];
      if(vectores[v]<-200){
        vectores[v]=-200;
        movvectores[v] = round(random(10));
      }
      if(vectores[v]>200){
        vectores[v]=200;
        movvectores[v] = round(random(-10,-1));
      }
    }
  }
}

  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "--full-screen", "--bgcolor=#666666", "--stop-color=#cccccc", "Proyecto" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
