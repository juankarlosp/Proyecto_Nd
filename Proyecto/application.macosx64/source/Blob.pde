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
  void display() {
    Rectangle r = contour.getBoundingBox();
    fill(0,0,0);
    noStroke();
    int posx=r.x+r.width/2;
    int posy=r.y+r.height/2;
    posx = round(map(posx, 0, 640, width, 0));
    posy = round(map(posy, 0, 480, 0, height));
    ellipse(posx, posy, tamano, tamano);
  }
  
  int[] getCoords() {
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
  void update(Contour newC) {
    
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
  void countDown() {    
    timer--;
  }

  // I am deed, delete me
  boolean dead() {
    if (timer < 0) return true;
    return false;
  }
  
  public Rectangle getBoundingBox() {
    return contour.getBoundingBox();
  }
}
