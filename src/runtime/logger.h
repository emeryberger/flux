#include <assert.h>
#include <stdio.h>
#include <math.h>
#include <stack>
#include <map>
#include <pthread.h>
#include "timer.h"
#include <string>
#include <vector>

#include <sys/types.h>
#include <sys/time.h>
#include <sys/resource.h>

//typedef u_int64_t ullong;

/**
 * Class Logger
 * Uses Emery Berger's Timer Class (included via timer.h) for wall-clock timing (milliseconds)
 * and the system call getrusage in for the cpu timeing (milliseconds)
 *
 * @author Alexander Kostadinov
 * 
 **/

class Logger 
{

	protected:
		//unsigned int ia_time_distr[DISTR_SIZE];
		//unsigned int completion_time_distr[DISTR_SIZE];  
        
	private:
		double total_ia_time;
		double total_cmp_time;
		double total_cpu_time;
		unsigned int num_cmps;
		unsigned int num_arrivals;
		int num_timers;
		bool first_time;
        
		flux::Timer ia_timer;
        
		std::map <int, flux::Timer *> completion_timer_map;
		std::map <int, bool> timer_availability_map;

		std::map <int, double> last_stime_val;
		std::map <int, double> last_utime_val;
		
		
		std::vector <double> *completions_vector;
		std::vector <double> *ia_vector;

		pthread_mutex_t *timer_mutex;
		pthread_rwlock_t *arrivals_rw_lock;
		pthread_rwlock_t *completion_rw_lock;
		pthread_rwlock_t *ia_rw_lock;
                
		pthread_mutex_t *cmp_avg_mutex;
		pthread_mutex_t *ia_avg_mutex;
		pthread_mutex_t *cpu_avg_mutex;

	private:
		int get_free_timer()
		{
			for (int i = 0; i < num_timers; i++)
			{
				if (timer_availability_map[i] == true)
				{
					timer_availability_map[i] = false;
					return i;
				}
			}
			
			completion_timer_map[num_timers] = new flux::Timer();
			timer_availability_map[num_timers] = false;
			num_timers++;
			
			return num_timers-1;
		}

	public:
		Logger ()
		{
			first_time = true;
			
			num_timers = 10;
			
			num_arrivals = 0;
			total_cmp_time = 0;
			total_cpu_time = 0.0;
			total_ia_time = 0;
			num_cmps = 0;
        
			completions_vector = new std::vector <double>;
			ia_vector = new std::vector <double>;

			cpu_avg_mutex = new pthread_mutex_t;
			pthread_mutex_init (cpu_avg_mutex, NULL);
			
			timer_mutex = new pthread_mutex_t;
			pthread_mutex_init (timer_mutex, NULL);

			cmp_avg_mutex = new pthread_mutex_t;
			pthread_mutex_init (cmp_avg_mutex, NULL);
			
			ia_avg_mutex = new pthread_mutex_t;
			pthread_mutex_init (ia_avg_mutex, NULL);

			completion_rw_lock = new pthread_rwlock_t;
			pthread_rwlock_init(completion_rw_lock, NULL);
        
			ia_rw_lock = new pthread_rwlock_t;
			pthread_rwlock_init(ia_rw_lock, NULL);
                
			arrivals_rw_lock = new pthread_rwlock_t;
			pthread_rwlock_init(arrivals_rw_lock, NULL);
            
			flux::Timer *timer_tmp;
			for (int i=0; i < num_timers; i++)
			{
				timer_tmp = new flux::Timer();
				completion_timer_map[i] = timer_tmp;
				timer_availability_map[i] = true;
				last_stime_val[i] = 0;
				last_utime_val[i] = 0;
			}
		}
        
		int start ()
		{

			pthread_rwlock_wrlock(arrivals_rw_lock);
			num_arrivals++;
			pthread_rwlock_unlock(arrivals_rw_lock);
        
			if (first_time)
			{
				ia_timer.start();
				first_time = false;
			}
			else
			{
				ia_timer.stop();
				double temp = (double) ia_timer;
				ia_timer.start();
				
				//log_ia(temp);
				
				pthread_mutex_lock(ia_avg_mutex);
				total_ia_time += 1000*temp;
				pthread_mutex_unlock(ia_avg_mutex);
			}
			
			pthread_mutex_lock(timer_mutex);
			int ret = get_free_timer();
			
			
			//printf("start: ");
			completion_timer_map[ret]->start();
			struct rusage usage;
			
			int r = getrusage(RUSAGE_SELF, &usage);

			timeval t_utime = usage.ru_utime;  // user time;
			timeval t_stime = usage.ru_stime;  // system time;
			
			last_utime_val [ret] = extract_time(t_utime);
			last_stime_val [ret] = extract_time(t_stime);

			
			pthread_mutex_unlock(timer_mutex);
			return ret;
		}
    
		double stop (int timer_num) 
		{
			// stop the wallclock timer
			
			struct rusage usage;
			int r = getrusage(RUSAGE_SELF, &usage);
			
			completion_timer_map[timer_num]->stop();
			// stop the cpu timer
			
			// increment the completions counter
			num_cmps++;
			
			
			double ret = 0.0;
			pthread_mutex_lock(timer_mutex);
			ret = (double) *completion_timer_map[timer_num];
			timer_availability_map[timer_num] = true;
			pthread_mutex_unlock(timer_mutex);
			
			
			//log_completions(ret);
			
			pthread_mutex_lock(cmp_avg_mutex);
			total_cmp_time += ret*1000;			// turn the seconds into milli-seconds
			pthread_mutex_unlock(cmp_avg_mutex);
			
			
			timeval t_utime = usage.ru_utime;  // user time;
			timeval t_stime = usage.ru_stime;  // system time;
			
			double utime = extract_time(t_utime);
			double stime = extract_time(t_stime);

			utime -= last_utime_val [timer_num];
			stime -= last_stime_val [timer_num];
			
			
			pthread_mutex_lock(cpu_avg_mutex);
			total_cpu_time += (utime + stime);
			//printf("total cpu time: %f\n" , total_cpu_time);
			pthread_mutex_unlock(cpu_avg_mutex);
			
			return ret;
		}
    
		double get_avg_ia_time()
		{
			double ret;
			pthread_mutex_lock(ia_avg_mutex);
			ret = total_ia_time / num_arrivals;
			pthread_mutex_unlock(ia_avg_mutex);
			return ret;
		}
		
		double get_avg_wallclock_time()
		{
			double ret;
			pthread_mutex_lock(cmp_avg_mutex);
			ret = total_cmp_time / num_cmps;
			pthread_mutex_unlock(cmp_avg_mutex);
			return ret;
		}
		
		double get_avg_cpu_time()
		{
			double ret = 0.0;
			pthread_mutex_lock(cpu_avg_mutex);
			ret = total_cpu_time / num_cmps;
			pthread_mutex_unlock(cpu_avg_mutex);
			
			return ret;
		}
		
		unsigned int get_num_arrivals()
		{
			return num_arrivals;
		}
		
		unsigned int get_num_cmps()
		{
			return num_cmps;
		}
		
		std::string print_ia_histogram()
		{
			std::string s = "";
			char buf[256];
		
			pthread_rwlock_wrlock(ia_rw_lock);
			sprintf(buf, "%d ", ia_vector->size());
			s += buf;
        
			while (ia_vector->size() > 0)
			{
				double d = ia_vector->at(ia_vector->size()-1);
            // lets have them as ints!
				sprintf(buf, "%d ", (int)d);
				ia_vector->pop_back();
				s += buf;
			}
			pthread_rwlock_unlock(ia_rw_lock);
        
			return s;
		}
    
		std::string print_completions_histogram()
		{
			std::string s = "";
			char buf[256];
			pthread_rwlock_wrlock(completion_rw_lock);
        
			sprintf(buf, "%d ", completions_vector->size());
			s += buf;
			while (completions_vector->size() > 0)
			{
				double d = completions_vector->at(completions_vector->size()-1);
            // lets have them as ints!
				sprintf(buf, "%d ", (int)d);
				completions_vector->pop_back();
				s += buf;
			}
			pthread_rwlock_unlock(completion_rw_lock);
        
			return s;
		}
    
		int print_num_arrivals()
		{
			int ret;
			pthread_rwlock_wrlock(arrivals_rw_lock);
			ret = num_arrivals;
			pthread_rwlock_unlock(arrivals_rw_lock);
			return ret;
        //printf("Number of Arrivals: %d", num_arrivals);
		}
    
		std::string getValues()
		{
			std::string s = "";
			char buf[256];
        
			sprintf(buf, "%d ", print_num_arrivals());
			s += buf;
			s += " " + print_ia_histogram() + " " + print_completions_histogram();
        
			return s;
        
		}
		
		void log_completions(double timeval)
		{
			pthread_rwlock_wrlock(completion_rw_lock);
			if (timeval < 0)
			{
				printf ("error.. negative time interval");
				pthread_rwlock_unlock(completion_rw_lock);
				return;
			}
        
			completions_vector->push_back(timeval*1000000);

			pthread_rwlock_unlock(completion_rw_lock);
		}
    
		/*
		void log_ia(double timeval)
		{
		pthread_rwlock_rdlock(ia_rw_lock);
		if (timeval < 0)
		{
		printf ("error.. negative time interval");
		pthread_rwlock_unlock(ia_rw_lock);
		return;
}
        
		ia_vector->push_back(timeval*1000000);

		pthread_rwlock_unlock(ia_rw_lock);
}
		*/
		/**
		 * Takes in a timeval stuct and extracts the number of milliseconds (10^-3) from it.
		 * t.tv_sec = number of seconds (Multiply the seconds by 1000 to get milliseconds)
		 * t.tv_usec = numer of microseconds (10^-6) (Divide the microseconds by 1000 to get milliseconds)
		 *
		 * @param t The timeval struct
		 * @return double represengting the number of milliseconds
		 **/
		double extract_time(timeval t)
		{
			double utime = ((double) t.tv_sec)*1000.0 + ((double)t.tv_usec) / 1000.0;
			//return (unsigned int) utime;
			return (double) utime;
		}
};
