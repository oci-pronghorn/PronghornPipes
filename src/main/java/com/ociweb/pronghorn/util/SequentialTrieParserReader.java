package com.ociweb.pronghorn.util;

public class SequentialTrieParserReader {

    final int maxValues = 16;
    
    private int[] pos = new int[maxValues];
    private int posIdx = 0;
    
    private int value = 0;

    private byte numericSign;
    private long numericValue;
    private byte numericLength;
    private byte numericBase;

//    
//    private int bytesPos;
//    private int bytesLen;
    
    
    public SequentialTrieParserReader() {
        
    }
    
    
    /**
     * Given a visitor reads every path unless visitor returns false on open, in that case that branch will not be followed.
     * When visit reaches the end the value of the byte array is returned.
     * 
     * Use Cases:
     * 
     *  We have a "template" and need to find all the existing paths that match it. For example find which known topics match a new subscription in MQTT.
     *  
     *  //NOTE: if we add the new type TYPE_VALUE_BYTES
     *  We have a new path and need to find all the values in the tree that match it.  For example find which known subscriptions a new topic should go to in MQTT. 
     * 
     * 
     *  
     * 
     */
    public void visit(SequentialTrieParser that, int i, ByteSquenceVisitor visitor) {
        
            switch (that.data[i]) {
                case SequentialTrieParser.TYPE_SAFE_END:

                        visitor.end(
                                (0XFFFF&that.data[i+1])
                               ); 

                    break;
                case SequentialTrieParser.TYPE_BRANCH_VALUE:
                    
                    int localJump = i + SequentialTrieParser.SIZE_OF_BRANCH;
                    int farJump   = i + ((that.data[i+2]<<16) | (0xFFFF&that.data[i+3])); 
                    
                    visit(that, localJump, visitor);
                    visit(that, farJump, visitor);
                    
                    break;
                case SequentialTrieParser.TYPE_RUN:
                    
                    final int run = that.data[i+1];
                    final int idx = i + SequentialTrieParser.SIZE_OF_RUN;
                    
                    if (visitor.open(that.data, idx, run)) {
                    
                        visit(that, idx+run, visitor);
                        visitor.close(run);
                    }
                    
                    break;
                case SequentialTrieParser.TYPE_VALUE_BYTES:
                    
                    //take all the bytes until we read the stop byte.
                    //is no body here so i + 2 is next
                    //open bytes
                    //visit
                    //close bytes
                    
                    break;
                case SequentialTrieParser.TYPE_VALUE_NUMERIC:
                    
                   
                    //take all the bytes that are ASCII numbers
                    //is no body here so i + 1 is next
                    //open int
                    //visit
                    //close int
                    
                    
                    break;
                case SequentialTrieParser.TYPE_END:

                        visitor.end(
                                (0XFFFF&that.data[i+1])
                               ); 

                    break;
                default:
                    throw new UnsupportedOperationException("ERROR Unrecognized value\n");
            }            
        
    }
    
    
    private byte[] sourceBacking;
    private int    sourcePos;
    private int    sourceLen;
    private int    sourceMask;
    
    
    public static void parseSetup(SequentialTrieParserReader that, byte[] source, int offset, int length, int mask) {
        
        that.sourceBacking = source;
        that.sourcePos     = offset;
        that.sourceLen     = length;
        that.sourceMask    = mask;        
        
    }
    
    public static int parseNext(SequentialTrieParserReader reader, SequentialTrieParser that) {
                
        return reader.query(that, reader.sourceBacking, reader.sourcePos, reader.sourceLen, reader.sourceMask);
                
    }
    
    public static void psrseSkip(SequentialTrieParserReader reader, SequentialTrieParser that, int count) {
        reader.sourcePos += count;
        reader.sourceLen -= count;
    }
    
    //TODO: if we stop at one parse how do we continue to the next?
    
    
    public int query(SequentialTrieParser that, 
                     byte[] source, int offset, int length, int mask) {
        
        int pos = 0;
        short[] data = that.data;
        int runLength = 0;
        int sum = 0;
        
        //TODO: store values in stack
        //TODO: store text as pos & length in source?
        
        boolean hasSafePoint = false;
        int     safeReturnValue = -1;
        
        int type = data[pos++];
        while (type != SequentialTrieParser.TYPE_END) {
               
            
            if (type==SequentialTrieParser.TYPE_BRANCH_VALUE) {
                pos = SequentialTrieParser.jumpOnBit((short) source[mask & offset], pos, data);
                
            } else if (type == SequentialTrieParser.TYPE_RUN) {
                //run
                int run = data[pos++];
        
                if (SequentialTrieParser.skipDeepChecks && !hasSafePoint) {
                    pos += run;
                    offset += run;
                } else {
                    
                    int r = run;
                    while (--r >= 0) {
                        if (data[pos++] != source[mask & offset++]) {
                            if (hasSafePoint) {                                
                                return this.value = safeReturnValue;
                            }
                            
                            throw new RuntimeException("check prev branch, no match at pos "+pos+"  \n"+that);
                                                        
                        }
                    }                        
                }
                runLength+=run;
                
            } else {
                if (type == SequentialTrieParser.TYPE_SAFE_END) {
                    
                    hasSafePoint = true;
                    safeReturnValue = SequentialTrieParser.readEndValue(data,pos);
                    pos += SequentialTrieParser.SIZE_OF_RESULT;
                    //if the following does not match we will return this safe value.
                    //we do not yet have enough info to decide if this is the end or not.
                    
                    if (length == runLength) {
                        //hard stop passed in forces us to use the safe point
                        return this.value = safeReturnValue;
                    }
                    

                } else {
                    if (type == SequentialTrieParser.TYPE_VALUE_NUMERIC) {
                        
                        int templateId = data[pos++];
                        
                        //TODO: move this conditional to insert so this code just pulls flags
                        switch(templateId) {
                                case SequentialTrieParser.ESCAPE_CMD_SIGNED_DEC:
                                    templateId = SequentialTrieParser.NUMERIC_FLAG_SIGN;
                                    
                                break;
                                case SequentialTrieParser.ESCAPE_CMD_UNSIGNED_DEC:
                                    templateId = 0;//SequentialTrieParser.NUMERIC_FLAG_SIGN;
                                
                                break;
                                case SequentialTrieParser.ESCAPE_CMD_SIGNED_HEX:
                                    templateId = SequentialTrieParser.NUMERIC_FLAG_SIGN;
                                    templateId |= SequentialTrieParser.NUMERIC_FLAG_HEX;
                                    
                                break;
                                case SequentialTrieParser.ESCAPE_CMD_UNSIGNED_HEX:
                                    templateId = 0;//SequentialTrieParser.NUMERIC_FLAG_SIGN;
                                    templateId |= SequentialTrieParser.NUMERIC_FLAG_HEX;
                                
                                break;
                                case SequentialTrieParser.ESCAPE_CMD_DECIMAL:
                                    templateId = 0;//SequentialTrieParser.NUMERIC_FLAG_SIGN;
                                    templateId |= SequentialTrieParser.NUMERIC_FLAG_DECIMAL;                                  
                                break;
                                case SequentialTrieParser.ESCAPE_CMD_RATIONAL:
                                    templateId = SequentialTrieParser.NUMERIC_FLAG_SIGN;
                                    templateId |= SequentialTrieParser.NUMERIC_FLAG_RATIONAL;
                                break;
                        }

                        parseNumeric(this,source,offset, length-runLength, mask, templateId);
                    } else if (type == SequentialTrieParser.TYPE_VALUE_BYTES) {
                        parseBytes(this,source,offset, length-runLength, mask, data[pos++]);
                    } else {
                        
                        System.out.println(that);
                        throw new UnsupportedOperationException("Bad jump length now at position "+(pos-1)+" type found "+type);
                        
                    }
                }
            } 
            
            type = data[pos++]; 
        }
        
        int lengthOfParse = offset-sourcePos; //TODO: store this?
        sourceLen -= lengthOfParse;
        sourcePos = offset;
        
        return value = SequentialTrieParser.readEndValue(data,pos);
        
        
    }

    
    private static int parseBytes(SequentialTrieParserReader reader, byte[] source, int sourcePos, int sourceLenght, int sourceMask, int stopValue) {
        
        int len = 0;
        int bytesPos = sourcePos;
        
        short c = 0;
        do {
            c = source[sourceMask & sourcePos++];                    
            if (stopValue != c) {
                len++;
                continue;
            } else {
                break;
            }
        }  while (true);
        
        return sourcePos;
    }
    
    private static int parseNumeric(SequentialTrieParserReader reader, byte[] source, int sourcePos, int sourceLength, int sourceMask, int numType) {
        
        byte sign = 1;
        long intValue = 0;
        byte intLength = 0;
        byte base=10;
        
        
        if (0!= (SequentialTrieParser.NUMERIC_FLAG_DECIMAL&numType)) {
            final short c = source[sourceMask & sourcePos];
            if ('.'!=c) {
                reader.numericSign = 1;
                reader.numericValue = 0;
                reader.numericLength = 1;
                reader.numericBase = 10;
                //do not parse numeric
                return sourcePos;
            } else {
                sourcePos++;
            }
            
        } else if (0!= (SequentialTrieParser.NUMERIC_FLAG_RATIONAL&numType)) {
            final short c = source[sourceMask & sourcePos];
            if ('/'!=c) {
                reader.numericSign = 1;
                reader.numericValue = 1;
                reader.numericLength = 1;
                reader.numericBase = 10;
                //do not parse numeric
                return sourcePos;
            } else {
                sourcePos++;
            }
            
        }
        
        //NOTE: these Numeric Flags are invariants consuming runtime resources, this tree could be pre-compiled to remove them if neded.
        if (0!=(SequentialTrieParser.NUMERIC_FLAG_SIGN&numType)) {
            final short c = source[sourceMask & sourcePos];
            if (c=='-') { //NOTE: check ASCII table there may be a fater way to do this.
                sign = -1;
                sourcePos++;
            }
            if (c=='+') {
                sourcePos++;
            }
        }
        
        //just to keep it from spinning on values that are way out of bounds
        sourceLength = 0x1F & sourceLength; //never scan over 32
        
        if (0==(SequentialTrieParser.NUMERIC_FLAG_HEX&numType) | ('0'!=source[sourceMask & sourcePos+1])| ('x'!=source[sourceMask & sourcePos+2])  ) {                            
            base = 10;
            short c = 0;
            do {
                c = source[sourceMask & sourcePos++];                    
                if ((c>='0') && (c<='9') && intLength<sourceLength) {
                    intValue = (intValue * 10)+(c-'0');
                    intLength++;
                    continue;
                } else {
                    break;
                }
            }  while (true);
            if (intLength>19) {
                //ERROR
                
            }
        } else {
            base = 16;
            sourcePos+=2;//skipping over the 0x checked above
            short c = 0;
            do {
                c = source[sourceMask & sourcePos++];
                
                if ((c>='0') && (c<='9') && intLength<sourceLength) {
                    intValue = (intValue<<4)+(c-'0');
                    intLength++;
                    continue;
                } else  if ((c>='a') && (c<='f') && intLength<sourceLength) {
                    intValue = (intValue<<4)+(10+(c-'a'));
                    intLength++;
                    continue;
                } else {
                    break;
                }
            }  while (true);
            
            if (intLength>16) {
                //ERROR
            }
            
        }
        
        
        //assign all values back
        reader.numericSign = sign;
        reader.numericValue = intValue;
        reader.numericLength = intLength;
        reader.numericBase = base;

        return sourcePos;
    }

    

}
