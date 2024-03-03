package test;

import dev.lukebemish.autoextension.AutoExtension;
import subproject.Subproject;

@AutoExtension
public class Test {
    public static void main(String[] args) {
        Subproject.doStuff();
    }
}
