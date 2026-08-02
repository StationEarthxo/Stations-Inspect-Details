package com.spinningitems;

public enum EquipmentSpinStyle
{
    LEFT_TO_RIGHT("Left-to-right spin"),
    HORIZONTAL_TUMBLE("Forward tumble"),
    MIXED("Mixed rotation");

    private final String label;

    EquipmentSpinStyle(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
