package dev.settledlands;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EnchantDedupTest {
    @Test void distinctOffersStay() {assertArrayEquals(new boolean[]{false,false,false},EnchantDedup.blanks(new int[]{7,23,30},new int[]{5,9,12},new int[]{1,1,2}));}
    @Test void emptyRowsAreIgnored() {assertArrayEquals(new boolean[]{false,false,false},EnchantDedup.blanks(new int[]{7,0,30},new int[]{5,-1,9},new int[]{1,-1,2}));}
    @Test void pricierDuplicateIsBlanked() {assertArrayEquals(new boolean[]{false,false,true},EnchantDedup.blanks(new int[]{7,23,30},new int[]{5,5,5},new int[]{1,2,2}));}
    @Test void threeIdenticalKeepOnlyCheapest() {assertArrayEquals(new boolean[]{false,true,true},EnchantDedup.blanks(new int[]{4,7,14},new int[]{5,5,5},new int[]{1,1,1}));}
    @Test void costTieKeepsEarlierRow() {assertArrayEquals(new boolean[]{false,true,false},EnchantDedup.blanks(new int[]{7,7,30},new int[]{5,5,9},new int[]{1,1,2}));}
    @Test void sameEnchantDifferentLevelsStay() {assertArrayEquals(new boolean[]{false,false,false},EnchantDedup.blanks(new int[]{7,23,30},new int[]{5,5,9},new int[]{1,2,2}));}
    @Test void sameLevelDifferentEnchantsStay() {assertArrayEquals(new boolean[]{false,false,false},EnchantDedup.blanks(new int[]{7,23,30},new int[]{5,6,5},new int[]{1,1,2}));}
}
