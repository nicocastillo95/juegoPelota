package com.shenkar.nik.pelotarebota;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;


@SuppressLint("ViewConstructor")
public class LogicaPelotaRebota extends SurfaceView implements Runnable {

    //puntajes
     private static final int PUNTAJEROJO = 10;
    private static final int PUNTAJEAMARILLO = 20;
    private static final int PUNTAJEVERDE = 30;

    // Este es el hilo del juego
    Thread hiloJuego = null;

    //variable de la vista que usara el thread hiloJuego
    SurfaceHolder vistaHiloJuego;

    // A boolean which we will set and unset
    // when the game is running- or not.
    volatile boolean estaJugando;

    //El juego al momento de iniciar esta en pausa
    boolean pausa = true;

    // variables usadas para establecer la superficie a "dibujar" y un "pincel"
    Canvas superficieDibujar;
    Paint pincel;

    // Esta variable determina los fotogramas por segundo del juego
    long fps;

    // Esta variable es usada para ayudar en el calculo de los fps
    private long tiempoPantallaCelular;

    // Tamaño de la pantalla en pixeles
    int pantallaCordX;
    int pantallaCordY;

    // variable de la paleta del juego
    Paleta paleta;

    // variable de la pelota del juego
    Pelota pelota;

    // arreglo de hasta 200 ladrillos
    Ladrillo[] ladrillos = new Ladrillo[200];
    int numLadrillo = 0;

    // Sonidos del juego
    SoundPool piletaSonido;
    int sonido1ID = -1;
    int sonido2ID = -1;
    int sonido3ID = -1;
    int vidaPerdidaID = -1;
    int explocionID = -1;

    // El puntaje
    int puntaje = 0;

    // vidas
    int canttVida = 1;

    TiempoVelPelota velPelota;

    Context mContext;

    //Constructor
    public LogicaPelotaRebota(Context context, int x, int y) {

        super(context);
        mContext = context;
        // Se inicializa los objetos vistaHiloJuego y pincel
        vistaHiloJuego = getHolder();
        pincel = new Paint();


        pantallaCordX = x;
        pantallaCordY = y;

        //Creacion de la paleta
        paleta = new Paleta(pantallaCordX, pantallaCordY);

        // Creacion de la pelota
        int r = (int) (Math.random() * 2) + 1;
        if (r == 1) {
            pelota = new Pelota(400);
        } else {
            pelota = new Pelota(-400);
        }
        velPelota = new TiempoVelPelota(mContext, 1000000, 2000, pelota);
        // cargar sonidos del juego
        piletaSonido = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);

        try {
            // Proporciona acceso a archivos externos
            AssetManager assetManager = context.getAssets();
            // Variable que sirve para leer archivos externos
            AssetFileDescriptor descriptor;

            // Caega nuestros sonidos listos para usar
            descriptor = assetManager.openFd("paletapelota.mp3");
            sonido1ID = piletaSonido.load(descriptor, 0);

            descriptor = assetManager.openFd("beep2.ogg");
            sonido2ID = piletaSonido.load(descriptor, 0);

            descriptor = assetManager.openFd("beep3.ogg");
            sonido3ID = piletaSonido.load(descriptor, 0);

            descriptor = assetManager.openFd("explocionpelota.ogg");
            vidaPerdidaID = piletaSonido.load(descriptor, 0);

            descriptor = assetManager.openFd("explode.ogg");
            explocionID = piletaSonido.load(descriptor, 0);

        } catch (IOException e) {
            // Print an error message to the console
            Log.e("error", "failed to load sound files");
        }

        crearLadrilloYReiniciar();

    }

    public void crearLadrilloYReiniciar() {

        //coloca la pelota en el centro
        pelota.reiniciar(pantallaCordX, pantallaCordY);
        int anchoLadrillo = pantallaCordX / 8;
        int altoLadrillo = pantallaCordY / 20;
        int columna, fila;
        // Se construye el muro de ladrillos
        numLadrillo = 0;

        for (columna = 0; columna < 8; columna++) {
            for (fila = 0; fila < 8; fila++) {
                if (fila < 3) {
                    ladrillos[numLadrillo] = new Ladrillo(fila, columna, anchoLadrillo, altoLadrillo, 'v', 2);
                    numLadrillo++;
                } else {
                    if (fila < 6 && columna % 2 == 0) {
                        ladrillos[numLadrillo] = new Ladrillo(fila, columna, anchoLadrillo, altoLadrillo, 'a', 1);
                        numLadrillo++;
                    } else {
                        if (fila > 5 && columna % 3 != 0) {
                            ladrillos[numLadrillo] = new Ladrillo(fila, columna, anchoLadrillo, altoLadrillo, 'r', 0);
                            numLadrillo++;
                        }
                    }
                }
            }

        }


        // si se pierde todas las vidas el puntaje y la cantidad de vida se reinicia
        if (canttVida == 0) {
            puntaje = 0;
            canttVida = 3;
        }


    }


    //en este metodo se encuentra lo que necesita ser actualizado, ya sea
    //movimiento, deteccion de colicion etc.

    public void actualizar() {
        // actualiza los movimientos de la pelota y la paleta
        paleta.actualizar(fps);
        pelota.actualizar(fps);

        // Comprueba si la pelota choca con un ladrillo
        for (int i = 0; i < numLadrillo; i++) {

            if (ladrillos[i].getColorLadrillo() == 'v') {
                if (ladrillos[i].getVisible()) {
                    if (RectF.intersects(ladrillos[i].getRect(), pelota.getRect())) {
                        if (ladrillos[i].getGolpe() == 0) {
                            ladrillos[i].setVisible();
                            pelota.contrarioY();
                            puntaje = puntaje + PUNTAJEVERDE;
                            piletaSonido.play(explocionID, 1, 1, 0, 0, 1);
                        } else {
                            ladrillos[i].restarGolpe();
                            pelota.contrarioY();
                        }
                    }

                }
            } else {
                if (ladrillos[i].getColorLadrillo() == 'a') {
                    if (ladrillos[i].getVisible()) {
                        if (RectF.intersects(ladrillos[i].getRect(), pelota.getRect())) {
                            if (ladrillos[i].getGolpe() == 0) {
                                ladrillos[i].setVisible();
                                pelota.contrarioY();
                                puntaje = puntaje + PUNTAJEAMARILLO;
                                piletaSonido.play(explocionID, 1, 1, 0, 0, 1);
                            } else {
                                ladrillos[i].restarGolpe();
                                pelota.contrarioY();
                            }
                        }

                    }
                } else {
                    if (ladrillos[i].getVisible()) {
                        if (RectF.intersects(ladrillos[i].getRect(), pelota.getRect())) {

                            ladrillos[i].setVisible();
                            pelota.contrarioY();
                            puntaje = puntaje + PUNTAJEROJO;
                            piletaSonido.play(explocionID, 1, 1, 0, 0, 1);

                        }

                    }
                }
            }
            if(puntaje == 80) {
                pausa = true;
                Intent intent = new Intent(mContext, FinalLevel1.class);
                mContext.startActivity(intent);
                ((Activity) mContext).finish();
            }
        }

        // comprueba si la pelota choca con la paleta
        if (RectF.intersects(paleta.getRect(), pelota.getRect())) {
            // pelota.setRandomX();
            pelota.contrarioY();
            pelota.actualizarY(paleta.getRect().top - 2);
            piletaSonido.play(sonido1ID, 1, 1, 0, 0, 1);
        }
        // comprueba si la pelota toca el fondo de la pantalla
        if (pelota.getRect().bottom > pantallaCordY) {
            pelota.contrarioY();
            pelota.actualizarY(pantallaCordY - 2);

            // Se pierde una vida
            canttVida--;
            piletaSonido.play(vidaPerdidaID, 1, 1, 0, 0, 1);
            pelota.reiniciar(pantallaCordX, pantallaCordY);
            paleta.reiniciar(pantallaCordX, pantallaCordY);
            if (canttVida == 0) {
                pausa = true;
                Intent intent = new Intent(mContext, GameOver.class);
                mContext.startActivity(intent);
                crearLadrilloYReiniciar();
                ((Activity) mContext).finish();
            }

        }

        // Rebota la pelota cuando toca la parte superior de la pantalla
        if (pelota.getRect().top < 0) {
            pelota.contrarioY();
            pelota.actualizarY(12);
            piletaSonido.play(sonido2ID, 1, 1, 0, 0, 1);
        }

        // Rebota la pelota cuando toca la parte izquierda de la pantalla
        if (pelota.getRect().left < 0) {
            pelota.contrarioX();
            pelota.actualizarX(2);
            piletaSonido.play(sonido3ID, 1, 1, 0, 0, 1);
        }

        // Rebota la pelota cuando toca la parte derecha de la pantalla
        if (pelota.getRect().right > pantallaCordX - 10) {
            pelota.contrarioX();
            pelota.actualizarX(pantallaCordX - 22);
            piletaSonido.play(sonido3ID, 1, 1, 0, 0, 1);
        }


        // Se pausa el juego al llegar al puntaje establecido
        if (puntaje == numLadrillo * 10) {
            pausa = true;
            crearLadrilloYReiniciar();
        }

    }

    //se dibuja la escena recien actualizada
    public void dibujar() {
        // verificamos que la superficie de dibujo sea valido
        if (vistaHiloJuego.getSurface().isValid()) {
            // bloquear la superficie lista para dibujar
            superficieDibujar = vistaHiloJuego.lockCanvas();

            // se dibuja el color de fondo
            superficieDibujar.drawColor(Color.argb(255, 0, 0, 0));

            //color del pincel a dibujar
            pincel.setColor(Color.argb(255, 255, 255, 255));

            // dibujar la paleta
            superficieDibujar.drawRect(paleta.getRect(), pincel);

            // dibujar la pelota
            superficieDibujar.drawOval(pelota.getRect(), pincel);
            // superficieDibujar.drawRect(pelota.getRect(), pincel);

            // dibujamos los ladrillos visibles
            for (int i = 0; i < numLadrillo; i++) {
                if (ladrillos[i].getVisible()) {
                    if (ladrillos[i].getColorLadrillo() == 'v') {
                        // cambiamos el color del pincel
                        pincel.setColor(Color.argb(255, 24, 200, 75));
                        superficieDibujar.drawRect(ladrillos[i].getRect(), pincel);
                    } else {
                        if (ladrillos[i].getColorLadrillo() == 'a') {
                            // cambiamos el color del pincel
                            pincel.setColor(Color.argb(255, 235, 249, 20));
                            superficieDibujar.drawRect(ladrillos[i].getRect(), pincel);
                        } else {
                            // cambiamos el color del pincel
                            pincel.setColor(Color.argb(255, 239, 38, 35));
                            superficieDibujar.drawRect(ladrillos[i].getRect(), pincel);
                        }
                    }
                }
            }


            // cambiamos el color del pincel
            pincel.setColor(Color.argb(255, 255, 255, 255));

            // dibujamos los numeros del puntaje
            pincel.setTextSize(40);
            superficieDibujar.drawText("Puntaje: " + puntaje + "   Vidas: " + canttVida, 10, 50, pincel);

            // el jugador supero el nivel
            if (puntaje == numLadrillo * 10) {
                pincel.setTextSize(90);
                superficieDibujar.drawText("NIVEL SUPERADO", 10, pantallaCordY / 2, pincel);
            }

            // El jugador perdio
            if (canttVida == 0) {
                pincel.setTextSize(90);
                superficieDibujar.drawText("FIN", 10, pantallaCordY / 2, pincel);
            }

            // se dibuja en la pantalla
            vistaHiloJuego.unlockCanvasAndPost(superficieDibujar);
        }
    }

    // si la actividad PelotaRebota se pausa o detiene
    // detenemos nuestro hilo
    public void pause() {
        estaJugando = false;
        try {
            hiloJuego.join();
        } catch (InterruptedException e) {
            Log.e("Error:", "joining thread");
        }
    }

    // si la actividad PelotaRebota es iniciado
    // iniciamos nuestro hilo
    public void resume() {
        estaJugando = true;
        hiloJuego = new Thread(this);
        hiloJuego.start();
    }


    @Override
    public void run() {
        velPelota.start();
        while (estaJugando) {
            // La variable startFrameTime guarda la hora actual en milisegundos
            long inicioTiempodePantalla = System.currentTimeMillis();
            float primerV = pelota.getRect().top;
            // actualizar pantalla
            if (!pausa) {
                actualizar();
            }
            // Dibujar en la pantalla
            dibujar();
            //calcular los fps de la pantalla
            tiempoPantallaCelular = System.currentTimeMillis() - inicioTiempodePantalla;
            float segundoV = pelota.getRect().top;
            float total = segundoV - primerV;
            velPelota.valorY(total);
            if (tiempoPantallaCelular >= 1) {
                fps = 1000 / tiempoPantallaCelular;
            }
        }
    }


    // The SurfaceView class implements onTouchListener
    // So we can override this method and detect screen touches.
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
            // Player has touched the screen
            case MotionEvent.ACTION_DOWN:
                pausa = false;
                if (motionEvent.getX() > pantallaCordX / 2) {
                    paleta.setMovmentState(paleta.DERECHA);
                } else {
                    paleta.setMovmentState(paleta.IZQUIERDA);
                }
                break;

            // Player has removed finger from screen
            case MotionEvent.ACTION_UP:
                paleta.setMovmentState(paleta.STOP);
                break;
        }

        return true;
    }
}