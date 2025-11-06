import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
public class LZWTool {
    private static int R;        // size of seed alphabet
    private static int L;       // number of codewords = 2^W
    private static int W;         // codeword width
    private static int minW = 9; //default min
    private static int maxW = 16; //default max
    private static String alphabetPath;
    private static String policy = "freeze"; //default policy

    public static void main(String[] args){
        String mode = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode":
                    mode = args[++i];
                    break;
                case "--minW":
                    minW = Integer.parseInt(args[++i]);
                    break;
                case "--maxW":
                    maxW = Integer.parseInt(args[++i]);
                    break;
                case "--policy":
                    policy = args[++i];
                    break;
                case "--alphabet":
                    alphabetPath = args[++i];
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(2);
            }
        }
        //check required arguments
        if (mode == null){
            System.err.println("Error: mode is required");
            System.exit(1);
        }
        //compression ignores these
        else if (mode.equals("compress")){
            if (minW > maxW){
                System.err.println("Error: maxW must be >= minW");
                System.exit(1);
            }
            if (alphabetPath == null){
                System.err.println("Error: alphabet path is required");
                System.exit(1);
            }
            File alphabet = new File(alphabetPath);
            if(!alphabet.exists()){
                System.err.println("Error: alphabet file not found");
                System.exit(1);
            }
        }

        //call compress/expand methods
        if(mode.equals("compress")){
            compress(alphabetPath, minW, maxW, policy);
        }
        else if(mode.equals("expand")){
            expand();
        }
        else{
            System.err.println("Error: mode not found");
            System.exit(1);
        }

    }

    public static ArrayList<String> readFile(String alphabetPath){
        ArrayList<String> symbols = new ArrayList<>();
        try(BufferedReader reader = new BufferedReader(new FileReader(alphabetPath))){
            String line = reader.readLine();
            while(line != null){
                line = line.trim();
                if (!line.isEmpty() && !symbols.contains(line)){
                    symbols.add(line);
                }
            }
        }
        catch (IOException e){
            System.err.println("Error reading file" + e.getMessage());
            System.exit(1);
        }
        return symbols;
    }

    public static void compress(String alphabetPath, int minW, int maxW, String policy) {
        //initialize time counter
        int time = 0;

        //read alphabet file
        ArrayList<String> alphabet = readFile(alphabetPath);
        R = alphabet.size();
        W = minW;
        L = 1 << W;

        TSTmod<Integer> st = new TSTmod<Integer>();
        Map<Integer, Map<String, Integer>> counters = new HashMap<>();
        for (int i = 0; i < R; i++){
            st.put(new StringBuilder(alphabet.get(i)), i);
            Map<String, Integer> input = new HashMap<>();
            counters.put(i, input.put(alphabet.get(i), ++time));
        }
        int code = R+1;  // R is codeword for EOF

        //header
        BinaryStdOut.write(minW);
        BinaryStdOut.write(maxW);
        BinaryStdOut.write(policy);
        BinaryStdOut.write(R);
        for(String s : alphabet){
            BinaryStdOut.write(s.charAt(0));
        }

        //initialize the current string
        if (BinaryStdIn.isEmpty()) return;
        StringBuilder current = new StringBuilder();
        //read and append the first char
        char c = BinaryStdIn.readChar();
        current.append(c);
        Integer codeword = st.get(current);
        //increment counters
        Map<String, Integer> pair = counters.get(codeword);
        if(policy.equals("lru")){
            pair.put(current.toString(), ++time);
        }
        else if(policy.equals("lfu")){
            pair.put(current.toString(), 1);
        }
        counters.put(codeword, pair);

        while (!BinaryStdIn.isEmpty()) {
            codeword = st.get(current);
            //read and append the next char to current
            c = BinaryStdIn.readChar();
            current.append(c);
            //update counters
            Map<String, Integer> p = counters.get(codeword);
            if(policy.equals("lru")){
                p.put(current.toString(), ++time);
            }
            else if(policy.equals("lfu")){
                p.put(current.toString(), 1);
            }
            counters.put(codeword, p);

            if(!st.contains(current)){
              BinaryStdOut.write(codeword, W);
              if (code < L)  {  // Add to symbol table if not full
                  st.put(current, code++);
              }
              else{
                //width adjustment
                if(W < maxW){
                    W++;
                    L = 1 << W;
                    st.put(current, code++);
                }
                //eviction policies
                else{
                    switch(policy){
                        case "reset":
                        //reinitialize codebook with original seed alphabet
                            st = new TSTmod<Integer>();
                            for (int i = 0; i < R; i++)
                                st.put(new StringBuilder(alphabet.get(i)), i);
                            code = R+1;
                            W = minW;
                            L = 1 << W;
                            break;
                        case "freeze":
                        //stop adding new entries
                            break;
                        case "lru":
                        //evict least recently used
                            int oldest = Integer.MAX_VALUE;
                            int evictCode = -1;
                            for(int i : counters.keySet()){
                                int t = counters.get(i).get(current); //gets time
                                if (t < oldest || t == oldest && i > evictCode){ //if tie, bigger codeword gets evicted
                                    oldest = t;
                                    evictCode = i;  
                                }
                            }
                            String codeString = counters.get(evictCode);
                            st.put(new StringBuilder(codeString), null);
                            st.put(current, evictCode);
                            Map<String, Integer> input = new HashMap<>();
                            input.put(current.toString(), ++time);
                            counters.put(evictCode, input); //sets new codeword time counter
                            break;
                        case "lfu":
                        //evict least frequently used
                            int leastFreq = Integer.MAX_VALUE;
                            int evictC = -1;
                            for(int i : counters.keySet()){
                                int f = counters.get(i).get(current); //get frequency
                                if (f < leastFreq || f == leastFreq && i > evictC){
                                    leastFreq = f;
                                    evictC = i;
                                }
                            }
                            String codeStr = counters.get(evictC);
                            st.put(new StringBuilder(codeStr), null);
                            st.put(current, evictC);
                            Map<String, Integer> input = new HashMap<>();
                            input.put(current.toString(), 1);
                            counters.put(evictCode, input);
                            break;
                    }
                }
              }
              //reset current to only last char
              current = new StringBuilder("" + c);
            }
        }

        //Write the codeword of whatever remains
        //in current
        if (current.length()>0){
            Integer cw = st.get(current);
            if(cw != null){
                BinaryStdOut.write(cw, W);
            }
        }


        BinaryStdOut.write(R, W); //Write EOF
        BinaryStdOut.close();
    }


    public static void expand() {
        //read header
        int minW = BinaryStdIn.readInt();
        int maxW = BinaryStdIn.readInt();
        String policy = BinaryStdIn.readString();
        int R = BinaryStdIn.readInt();


        String[] st = new String[L];
        int i; // next available codeword value

        // initialize symbol table with all 1-character strings
        for (i = 0; i < R; i++)
            st[i] = "" + (char) i;
        st[i++] = "";                        // (unused) lookahead for EOF

        int codeword = BinaryStdIn.readInt(W);
        String val = st[codeword];

        while (true) {
            BinaryStdOut.write(val);
            codeword = BinaryStdIn.readInt(W);
            if (codeword == R) break;
            String s = st[codeword];
            if (i == codeword) s = val + val.charAt(0);   // special case hack
            if (i < L) st[i++] = val + s.charAt(0);
            val = s;

        }
        BinaryStdOut.close();
    }
}