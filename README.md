* Simple helper class to cache get, post, and general functions.
* Runs the compute function if redis key was not present, and caches the result.
* Where compute functions were run, a non-blocking thread pause will occur.
