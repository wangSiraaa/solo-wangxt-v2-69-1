package com.example.loan.domain;

/**
 * 还款方式。
 */
public enum RepaymentMethod {

    /** 等额本息：每月还款额固定，本金占比逐月递增。 */
    EQUAL_INSTALLMENT("等额本息"),

    /** 等额本金：每月偿还固定本金，利息逐月递减，月供逐月递减。 */
    EQUAL_PRINCIPAL("等额本金");

    private final String label;

    RepaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
