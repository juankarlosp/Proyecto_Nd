/**
 * Basado en Image Filtering
 * Funciona para Processing 2
 
 * It requires the ControlP5 Processing library:
 * http://www.sojamo.de/libraries/controlP5/
 */

import gab.opencv.*;
import java.awt.Rectangle;
import processing.video.*;
import controlP5.*;

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

float contrast = 0.90;
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

void setup() {
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

void draw() {

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
    scale(0.5);
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

void displayImages() {
  pushMatrix();
  scale(0.5);
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
void displayBlobs() {

  for (Blob b : blobList) {
    //strokeWeight(1);
    b.display();
  }
}
void displayConexiones() {

  for (Conexion cc : conexionList) {
    cc.pintarConexion();
  }
}
void displayContours() {

  for (int i=0; i<contours.size (); i++) {

    Contour contour = contours.get(i);

    noFill();
    stroke(0, 255, 0);
    strokeWeight(3);
    contour.draw();
  }
}

void displayContoursCenters() {

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

void detectBlobs() {

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

ArrayList<Contour> getBlobsFromContours(ArrayList<Contour> newContours) {

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

void detectConexion() {
  println("Tamaño de blobList: " + blobList.size());
  println("Tamaño de conexionList: " + conexionList.size());
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

void initControls() {
  // Slider for contrast
  cp5.addSlider("contrast")
    .setLabel("contrast")
      .setPosition(20, 50)
        .setRange(0.0, 6.0)
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

void toggleAdaptiveThreshold(boolean theFlag) {

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

void setLock(Controller theController, boolean theValue) {

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

void keyPressed() {
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

