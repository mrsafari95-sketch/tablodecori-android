package com.tablodecori.app.pricing
import java.math.BigDecimal
import java.math.MathContext

object FormulaEvaluator {
    private val mc=MathContext.DECIMAL64
    fun evaluate(formula:String,variables:Map<String,BigDecimal>):BigDecimal{
        require(formula.isNotBlank()){"فرمول خالی است."};val p=Parser(formula,variables);val r=p.expression();p.skip()
        require(p.end()){"عبارت اضافی در فرمول وجود دارد."};require(r.abs()<=BigDecimal("1000000000000000")){"خروجی فرمول بیش از حد بزرگ است."};return r
    }
    private class Parser(val s:String,val vars:Map<String,BigDecimal>){var i=0;fun end()=i>=s.length;fun skip(){while(i<s.length&&s[i].isWhitespace())i++}
        fun expression():BigDecimal{var v=term();while(true){skip();v=when{take('+')->v.add(term(),mc);take('-')->v.subtract(term(),mc);else->return v}}}
        fun term():BigDecimal{var v=factor();while(true){skip();v=when{take('*')->v.multiply(factor(),mc);take('/')->{val d=factor();require(d.compareTo(BigDecimal.ZERO)!=0){"تقسیم بر صفر مجاز نیست."};v.divide(d,mc)};else->return v}}}
        fun factor():BigDecimal{skip();if(take('+'))return factor();if(take('-'))return factor().negate();if(take('(')){val v=expression();require(take(')')){"پرانتز بسته نشده است."};return v};if(i<s.length&&(s[i].isDigit()||s[i]=='.'))return number();return variable()}
        fun number():BigDecimal{val st=i;var dot=false;while(i<s.length&&(s[i].isDigit()||s[i]=='.')){if(s[i]=='.'){require(!dot){"عدد نامعتبر است."};dot=true};i++};return s.substring(st,i).toBigDecimal()}
        fun variable():BigDecimal{val st=i;while(i<s.length&&(s[i].isLetterOrDigit()||s[i]=='_'))i++;require(i>st){"عبارت نامعتبر است."};val n=s.substring(st,i);return vars[n]?:error("متغیر «$n» شناخته نشد.")}
        fun take(c:Char):Boolean{skip();return if(i<s.length&&s[i]==c){i++;true}else false}
    }
}
