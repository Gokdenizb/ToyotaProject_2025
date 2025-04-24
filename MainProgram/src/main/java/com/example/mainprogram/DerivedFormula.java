package com.example.mainprogram;

/**
 * derived-formulas.json içindeki her bir obje,
 * symbol, formulaBid ve formulaAsk alanlarını taşır.
 */
public class DerivedFormula {
    private String symbol;

    private String formulaBid;

    private String formulaAsk;

    public DerivedFormula() {}

    public String getSymbol() {
        return symbol;
    }
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getFormulaBid() {
        return formulaBid;
    }
    public void setFormulaBid(String formulaBid) {
        this.formulaBid = formulaBid;
    }

    public String getFormulaAsk() {
        return formulaAsk;
    }
    public void setFormulaAsk(String formulaAsk) {
        this.formulaAsk = formulaAsk;
    }
}
