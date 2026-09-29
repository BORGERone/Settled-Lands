package dev.settledlands;
/** Pure duplicate-row rule for the enchanting table, kept free of Minecraft types so it is unit-testable. */
public final class EnchantDedup {
    /** Marks rows to blank. The three arrays are parallel vanilla rows: price, clue and level.
     *  A row is offered only when its cost is above zero and its clue is set.
     *  Rows showing the same (clue, level) keep only the cheapest; a cost tie keeps the earlier row. */
    public static boolean[] blanks(int[] costs,int[] clues,int[] levels) {
        boolean[] blanks=new boolean[costs.length];
        for(int i=0;i<costs.length;i++) {
            if(!offered(costs,clues,i))continue;
            for(int j=0;j<costs.length;j++) {
                if(j==i||!offered(costs,clues,j))continue;
                if(clues[j]==clues[i]&&levels[j]==levels[i]&&(costs[j]<costs[i]||costs[j]==costs[i]&&j<i)) {blanks[i]=true;break;}
            }
        }
        return blanks;
    }
    private static boolean offered(int[] costs,int[] clues,int i) {return costs[i]>0&&clues[i]>=0;}
    private EnchantDedup() {}
}
