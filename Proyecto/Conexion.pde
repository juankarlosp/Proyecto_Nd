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
  color col;
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
    int c=round(random(.5, 5.4));
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
  void pintarConexion(){
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
  void actualizarConexion(int pos1x, int pos1y, int pos2x, int pos2y, int pos3x, int pos3y){
    pos1X=pos1x;
    pos1Y=pos1y;
    pos2X=pos2x;
    pos2Y=pos2y;
    pos3X=pos3x;
    pos3Y=pos3y;
  }
  void moverConexion() {
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

