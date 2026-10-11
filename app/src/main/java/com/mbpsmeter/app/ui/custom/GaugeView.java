package com.mbpsmeter.app.ui.custom;
import android.content.Context; import android.graphics.*; import android.util.AttributeSet; import android.view.View;
public class GaugeView extends View {
    private int value=-70, min=-100, max=-30;
    private Paint bgPaint, progressPaint, textPaint, unitPaint;
    public GaugeView(Context c, AttributeSet a){super(c,a); init();}
    private void init(){
        bgPaint=new Paint(Paint.ANTI_ALIAS_FLAG); bgPaint.setStyle(Paint.Style.STROKE); bgPaint.setStrokeWidth(20f); bgPaint.setColor(Color.parseColor("#334155")); bgPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint=new Paint(Paint.ANTI_ALIAS_FLAG); progressPaint.setStyle(Paint.Style.STROKE); progressPaint.setStrokeWidth(20f); progressPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint=new Paint(Paint.ANTI_ALIAS_FLAG); textPaint.setColor(Color.WHITE); textPaint.setTextSize(80f); textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setFakeBoldText(true);
        unitPaint=new Paint(Paint.ANTI_ALIAS_FLAG); unitPaint.setColor(Color.parseColor("#94A3B8")); unitPaint.setTextSize(28f); unitPaint.setTextAlign(Paint.Align.CENTER);
    }
    public void setValueWithColor(int v){
        value=v;
        if(v>=-80) progressPaint.setColor(Color.parseColor("#22C55E"));
        else if(v>=-95) progressPaint.setColor(Color.parseColor("#F59E0B"));
        else progressPaint.setColor(Color.parseColor("#EF4444"));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas); int w=getWidth(), h=getHeight(); int size=Math.min(w,h)-40; float cx=w/2f, cy=h/2f+10;
        RectF rect=new RectF(cx-size/2f, cy-size/2f, cx+size/2f, cy+size/2f);
        canvas.drawArc(rect, 135, 270, false, bgPaint);
        float percent=(float)(value-min)/(max-min); percent=Math.max(0,Math.min(1,percent));
        canvas.drawArc(rect, 135, 270*percent, false, progressPaint);
        canvas.drawText(value+"", cx, cy+15, textPaint);
        canvas.drawText("dBm", cx, cy+50, unitPaint);
    }
}