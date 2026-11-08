function hex = fileSha256(filePath)
%FILESHA256 Hex SHA-256 digest of a file.
%   hex = fileSha256(filePath) returns the 64-character digest as a string.
%   Uses the JVM's SHA-256 implementation, which is always available inside
%   MATLAB.

filePath = string(filePath);
identifier = fopen(filePath, "r");
if identifier < 0
    error("synthNotes:fileRead", "Cannot open file '%s' for reading.", filePath);
end
bytes = fread(identifier, Inf, "uint8=>uint8");
fclose(identifier);
digest = java.security.MessageDigest.getInstance("SHA-256");
digest.update(typecast(bytes, "int8"));
hashBytes = typecast(digest.digest(), "uint8");
hex = lower(string(reshape(dec2hex(hashBytes, 2).', 1, [])));
end
