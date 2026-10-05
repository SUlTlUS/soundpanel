package dev.glass.soundbar

import android.graphics.*

internal object Glyphs {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    fun draw(c: Canvas,glyph: String,color: Int) {
        p.color=color; p.style=Paint.Style.FILL; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
        when(glyph) {
            "headphones" -> {
                p.style=Paint.Style.STROKE; p.strokeWidth=2.8f; c.drawArc(-9f,-10f,9f,10f,180f,180f,false,p)
                p.style=Paint.Style.FILL
                c.drawRoundRect(-10f,-1f,-5f,10f,2f,2f,p); c.drawRoundRect(5f,-1f,10f,10f,2f,2f,p)
            }
            "bell" -> {
                path.reset(); path.moveTo(-9f,6f); path.cubicTo(-6f,3f,-6f,0f,-6f,-4f)
                path.cubicTo(-6f,-13f,6f,-13f,6f,-4f); path.cubicTo(6f,0f,6f,3f,9f,6f); path.close()
                c.drawPath(path,p); c.drawRoundRect(-10f,5f,10f,7f,1f,1f,p); c.drawCircle(0f,-10f,2.4f,p); c.drawArc(-3f,7f,3f,12f,0f,180f,false,p)
            }
            "alarm" -> {
                c.drawCircle(0f,0f,9f,p); c.save(); c.rotate(-35f); c.drawOval(-13f,-10f,-6f,-5f,p); c.drawOval(6f,-10f,13f,-5f,p); c.restore()
                p.style=Paint.Style.STROKE; p.strokeWidth=2f; c.drawLine(-5f,7f,-8f,11f,p); c.drawLine(5f,7f,8f,11f,p)
                p.color=Color.WHITE; c.drawLine(0f,-6f,0f,0f,p); c.drawLine(0f,0f,5f,0f,p)
            }
            "moon" -> {
                val a=Path().apply { addCircle(0f,0f,10f,Path.Direction.CW) }
                val b=Path().apply { addCircle(5f,-5f,9f,Path.Direction.CW) }
                a.op(b,Path.Op.DIFFERENCE); c.drawPath(a,p)
            }
            "person" -> {
                c.drawCircle(-2f,-4f,4.2f,p); c.drawRoundRect(-10f,2f,6f,9f,3f,3f,p)
                path.reset(); path.moveTo(8f,-13f); path.lineTo(9.5f,-8f); path.lineTo(14f,-6f); path.lineTo(9.5f,-4.5f); path.lineTo(8f,0f); path.lineTo(6.5f,-4.5f); path.lineTo(2f,-6f); path.lineTo(6.5f,-8f); path.close(); c.drawPath(path,p)
            }
        }
        p.style=Paint.Style.FILL
    }
}
